// 화면 그리기 + 입력 처리 + 온라인 대전(P2P) 동기화.
// 온라인 구조: 방을 만든 쪽(host)의 브라우저가 게임 상태의 유일한 원본을 갖는다.
// 참가자(guest)는 "카드 뽑기/수 두기" 같은 행동만 보내고, host가 검증·적용한 뒤 전체 상태를 다시 내려준다.
(() => {
  const E = ChessUno, $ = (id) => document.getElementById(id);
  const ONLINE = window.CU_ONLINE !== false && typeof Peer === 'function' && 'RTCPeerConnection' in window;
  const PEER_OPTS = window.CU_PEER_OPTS || {};
  const PREFIX = 'chessuno1-';

  let g, sel = null, targets = [], pendingPromo = null, flipped = false, shownSeq = -1;
  let net = null; // 온라인일 때만: { role:'host'|'guest', code, peer, status, ... }

  const store = {
    get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* 저장 불가 환경은 무시 */ } },
    del(k) { try { localStorage.removeItem(k); } catch (e) { /* 무시 */ } },
  };

  // ---------- 행동 처리 ----------
  const myColor = () => (!net ? null : net.role === 'host' ? net.hostColor : net.myColor);
  const canAct = () => g.phase !== 'over' && !pendingPromo && (!net || (myColor() === g.turn && (net.role === 'host' || net.status === 'ok')));

  // 행동 하나를 게임 상태에 적용 (host와 로컬 모드에서만 실행)
  function apply(a) {
    if (a.t === 'draw') E.drawCard(g);
    else if (a.t === 'wild') E.chooseWild(g, a.n);
    else if (a.t === 'move') E.playMove(g, { from: a.from, to: a.to }, a.promo);
    else if (a.t === 'new') { g = E.newGame(); if (net) net.hostColor = E.opp(net.hostColor); }
  }

  function act(a) {
    sel = null; targets = []; pendingPromo = null;
    if (net && net.role === 'guest') { if (net.conn && net.conn.open) net.conn.send(a); return; }
    apply(a);
    if (net) broadcast();
    render();
  }

  // ---------- 온라인: host ----------
  const seatOnline = () => net.conns.some((c) => c.open && c.token === net.seat && Date.now() - c.lastSeen < 12000);

  function sendState(conn) {
    if (!conn.open) return;
    conn.send({ t: 'state', g: { ...g, deck: g.deck.length, rng: undefined }, you: conn.token === net.seat ? E.opp(net.hostColor) : null });
  }

  function broadcast() {
    store.set('cu-room-' + net.code, { g, hostColor: net.hostColor, seat: net.seat });
    net.conns.forEach(sendState);
  }

  function hostRoom(code) {
    const saved = store.get('cu-room-' + code);
    net = { role: 'host', code, status: 'connecting', conns: [], seat: null, hostColor: 'w', online: false, tries: 0 };
    if (saved && saved.g) { g = saved.g; net.hostColor = saved.hostColor || 'w'; net.seat = saved.seat || null; }
    else { g = E.newGame(); store.set('cu-room-' + code, { g, hostColor: 'w', seat: null }); }
    openHostPeer();
    setInterval(() => {
      if (!net.peer.destroyed && net.peer.disconnected) net.peer.reconnect();
      net.conns = net.conns.filter((c) => c.open || Date.now() - c.lastSeen < 12000);
      const on = seatOnline();
      if (on !== net.online) { net.online = on; render(); }
    }, 3000);
  }

  function openHostPeer() {
    const peer = net.peer = new Peer(PREFIX + net.code, PEER_OPTS);
    peer.on('open', () => { net.status = 'ok'; net.tries = 0; render(); });
    peer.on('connection', (conn) => {
      conn.lastSeen = Date.now(); conn.token = null;
      net.conns.push(conn);
      conn.on('data', (m) => onHostData(conn, m));
      conn.on('close', () => { net.online = seatOnline(); render(); });
    });
    peer.on('error', (err) => {
      // unavailable-id: 같은 방 주소가 아직 중계 서버에 남아 있음 (새로고침 직후 또는 다른 탭에서 열려 있음)
      if (err.type === 'unavailable-id' && net.tries++ < 8) { net.status = 'retry'; render(); setTimeout(() => { peer.destroy(); openHostPeer(); }, 2500); }
      else if (!peer.open) { net.status = 'error'; net.err = err.type; render(); }
    });
  }

  function onHostData(conn, m) {
    if (!m || typeof m !== 'object') return;
    conn.lastSeen = Date.now();
    if (m.t === 'hello') {
      conn.token = String(m.token || '');
      if (!net.seat || !seatOnline()) net.seat = conn.token; // 빈 자리(또는 끊긴 자리)는 새로 온 사람이 앉는다
      net.online = seatOnline();
      broadcast(); render();
    } else if (m.t === 'ping') conn.send({ t: 'pong' });
    else if (conn.token && conn.token === net.seat) {
      const guestColor = E.opp(net.hostColor);
      const ok = m.t === 'new' ? g.phase === 'over' : ['draw', 'wild', 'move'].includes(m.t) && g.phase !== 'over' && g.turn === guestColor;
      if (ok) { apply(m); broadcast(); render(); } else sendState(conn);
    }
  }

  // ---------- 온라인: guest ----------
  function joinRoom(code) {
    let token = store.get('cu-token');
    if (!token) { token = Math.random().toString(36).slice(2) + Date.now().toString(36); store.set('cu-token', token); }
    net = { role: 'guest', code, status: 'connecting', myColor: null, conn: null, connAt: 0, lastPong: 0, token, got: false };
    g = E.newGame();
    openGuestPeer();
    setInterval(guestLoop, 2500);
  }

  function openGuestPeer() {
    const peer = net.peer = new Peer(PEER_OPTS);
    peer.on('open', guestLoop);
    peer.on('error', (err) => {
      if (err.type === 'peer-unavailable') { net.conn = null; setStatus('nohost'); } // host가 접속해 있지 않음
    });
  }

  function setStatus(s) { if (net.status !== s) { net.status = s; render(); } }

  // 주기적으로 연결 상태를 점검하고, 끊겼으면 다시 붙는다
  function guestLoop() {
    const n = net, now = Date.now();
    if (n.conn && n.conn.open) {
      if (now - n.lastPong > 12000) { try { n.conn.close(); } catch (e) { /* 무시 */ } n.conn = null; setStatus('lost'); }
      else { n.conn.send({ t: 'ping' }); return; }
    }
    if (n.peer.destroyed) { openGuestPeer(); return; }
    if (n.peer.disconnected) { n.peer.reconnect(); return; }
    if (!n.peer.open) return;
    if (n.conn && now - n.connAt < 8000) return; // 연결 시도 중
    if (n.conn) { try { n.conn.close(); } catch (e) { /* 무시 */ } }
    const conn = n.conn = n.peer.connect(PREFIX + n.code, { reliable: true, serialization: 'json' });
    n.connAt = now;
    conn.on('open', () => { if (n.conn !== conn) return; n.lastPong = Date.now(); conn.send({ t: 'hello', token: n.token }); });
    conn.on('data', (m) => {
      if (n.conn !== conn || !m) return;
      n.lastPong = Date.now();
      if (m.t !== 'state') return;
      g = m.g; g.deck = new Array(m.g.deck); // guest는 덱 내용을 받지 않고 남은 장수만 안다
      n.myColor = m.you; n.got = true; n.status = 'ok';
      sel = null; targets = []; pendingPromo = null;
      render();
    });
    conn.on('close', () => { if (n.conn === conn) { n.conn = null; setStatus('lost'); } });
  }

  // ---------- 방 만들기 / 나가기 ----------
  const roomOfHash = () => { const m = /^#room-([a-z0-9]{4,12})$/i.exec(location.hash); return m ? m[1].toLowerCase() : null; };
  const roomLink = () => location.href.split('#')[0] + '#room-' + net.code;

  function createRoom() {
    const abc = 'abcdefghjkmnpqrstuvwxyz23456789';
    let code = '';
    for (let i = 0; i < 6; i++) code += abc[Math.floor(Math.random() * abc.length)];
    store.set('cu-room-' + code, { g, hostColor: 'w', seat: null }); // 지금 두던 판을 그대로 방으로 가져간다
    location.hash = 'room-' + code; // hashchange → 새로고침되며 host로 시작
  }

  function leaveRoom() {
    if (net.role === 'host') store.del('cu-room-' + net.code);
    try { net.peer.destroy(); } catch (e) { /* 무시 */ }
    history.replaceState(null, '', location.href.split('#')[0]);
    location.reload();
  }

  // ---------- 그리기 ----------
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
  const btn = (cls, text, fn) => { const b = el('button', cls, text); b.type = 'button'; b.addEventListener('click', fn); return b; };

  function cardEl(k) {
    const face = k.kind === 'num' ? String(k.n) : { skip: '⊘', reverse: '⇄', draw2: '+2', wild: 'WILD', wild4: '+4' }[k.kind];
    const c = el('div', `card ${k.color}${k.kind === 'wild' ? ' sm' : ''}${k.dim ? ' dim' : ''}`);
    c.appendChild(el('span', '', face));
    c.title = { num: `숫자 ${k.n}`, skip: 'Skip', reverse: 'Reverse', draw2: 'Draw Two', wild: 'Wild', wild4: 'Wild Draw Four' }[k.kind];
    return c;
  }

  function playerBar(node, c) {
    node.textContent = '';
    node.appendChild(el('div', `avatar pc ${c}k`));
    const who = el('span', 'who', E.NAME[c]);
    if (net && myColor()) who.appendChild(el('small', '', c === myColor() ? ' (나)' : ' (상대)'));
    node.appendChild(who);
    const t = el('div', 'taken');
    const order = 'qrbnp';
    g.taken[c].slice().sort((a, b) => order.indexOf(a[1]) - order.indexOf(b[1])).forEach((p) => t.appendChild(el('i', `pc ${p}`)));
    node.appendChild(t);
    if (g.penalty[c]) node.appendChild(el('span', 'badge pen', '벌칙: 2장 중 작은 수'));
    if (g.phase !== 'over' && g.turn === c) node.appendChild(el('span', 'badge turn', '차례'));
  }

  let netSig = '';
  function renderNet() {
    const box = $('net');
    $('mkroom').hidden = !ONLINE || !!net;
    $('autoflip-l').hidden = !!net;
    $('newgame').hidden = !!net && net.role !== 'host';
    box.hidden = !net;
    if (!net) return;
    let dot = 'wait', text;
    if (net.role === 'host') {
      if (net.status === 'connecting') text = '방을 여는 중…';
      else if (net.status === 'retry') text = '방을 다시 여는 중… (다른 탭에 같은 방이 열려 있으면 닫아 주세요)';
      else if (net.status === 'error') { dot = 'bad'; text = `중계 서버에 연결하지 못했습니다 (${net.err}). 새로고침해 보세요.`; }
      else if (net.online) { dot = 'ok'; text = '상대가 접속해 있습니다.'; }
      else if (net.seat) { dot = 'bad'; text = '상대 연결이 끊겼습니다. 같은 링크로 다시 들어오면 이어집니다.'; }
      else text = '상대를 기다리는 중 — 아래 링크를 보내세요.';
    } else if (net.status === 'ok') { dot = 'ok'; text = net.myColor ? `연결됨 — 나는 ${E.NAME[net.myColor]}입니다.` : '관전 중 (자리가 이미 찼습니다).'; }
    else if (net.status === 'nohost') { dot = 'bad'; text = '방장이 접속해 있지 않습니다. 계속 다시 시도하는 중…'; }
    else if (net.status === 'lost') { dot = 'bad'; text = '연결이 끊겼습니다. 다시 연결하는 중…'; }
    else text = '방에 연결하는 중…';
    const sig = [net.role, dot, text].join('|');
    if (sig === netSig) return;
    netSig = sig;
    box.textContent = '';
    const st = el('div', 'state');
    st.appendChild(el('i', 'dot ' + dot));
    st.appendChild(el('span', '', text));
    box.appendChild(st);
    const row = el('div', 'row');
    if (net.role === 'host') {
      const inp = el('input'); inp.id = 'roomlink'; inp.readOnly = true; inp.value = roomLink();
      inp.setAttribute('aria-label', '방 링크');
      inp.addEventListener('focus', () => inp.select());
      row.appendChild(inp);
      const cp = btn('btn', '링크 복사', () => {
        const done = () => { cp.textContent = '복사됨'; setTimeout(() => { cp.textContent = '링크 복사'; }, 1500); };
        const fallback = () => { inp.focus(); inp.select(); };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(inp.value).then(done, fallback); else fallback();
      });
      row.appendChild(cp);
    }
    row.appendChild(btn('btn', '방 나가기', leaveRoom));
    box.appendChild(row);
  }

  function render() {
    const over = g.phase === 'over', mine = myColor();
    if (net) flipped = mine === 'b';
    else if ($('autoflip').checked && !over) flipped = g.turn === 'b';
    const active = canAct();

    const board = $('board');
    board.textContent = '';
    const chk = !over && E.inCheck(g.s, g.turn) ? E.kingSq(g.s.b, g.turn) : -1;
    for (let v = 0; v < 64; v++) {
      const i = flipped ? 63 - v : v, r = i >> 3, f = i % 8, p = g.s.b[i];
      const s = el('button', 'sq' + ((r + f) % 2 ? ' dk' : ''));
      s.type = 'button';
      s.setAttribute('aria-label', E.sqName(i) + (p ? ' ' + p : ''));
      if (g.hi && (g.hi.from === i || g.hi.to === i)) s.classList.add('hi');
      if (sel === i) s.classList.add('sel');
      if (chk === i) s.classList.add('chk');
      const t = targets.find((m) => m.to === i);
      if (t) { s.classList.add('can'); if (p || t.flag === 'ep') s.classList.add('cap'); }
      if (active && g.phase === 'move' && p && p[0] === g.turn) s.classList.add('mine');
      if (v % 8 === 0) s.appendChild(el('span', 'co rk', String(8 - r)));
      if (v >= 56) s.appendChild(el('span', 'co fl', 'abcdefgh'[f]));
      if (p) s.appendChild(el('span', `pc ${p}`));
      s.addEventListener('click', () => clickSq(i));
      board.appendChild(s);
    }
    playerBar($('p-top'), flipped ? 'w' : 'b');
    playerBar($('p-bot'), flipped ? 'b' : 'w');

    const name = E.NAME[g.turn], st = $('status');
    const who = !net || !mine ? `${name} 차례` : g.turn === mine ? `내 차례 (${name})` : `상대 차례 (${name})`;
    const waiting = net && mine && g.turn !== mine;
    st.textContent = '';
    let head, sub = g.cardNote;
    if (net && net.role === 'guest' && !net.got) { head = '방에 연결하는 중…'; sub = ''; }
    else if (over) { head = `${E.NAME[g.winner]} 승리`; sub = g.winReason; }
    else if (g.phase === 'draw') { head = waiting ? `${who} — 카드를 뽑는 중` : `${who} — 카드를 뽑으세요`; if (g.penalty[g.turn]) sub = '벌칙 적용: 2장을 뽑아 작은 숫자만 씁니다.'; }
    else if (g.phase === 'wild') head = waiting ? `${who} — Wild 숫자를 고르는 중` : `${who} — Wild! 숫자를 고르세요`;
    else {
      head = `${who} — 남은 수 ${g.movesLeft} / ${g.movesTotal}`;
      if (g.movesLeft === 1 && g.movesTotal > 1) sub = '마지막 수입니다. 킹이 안전해야 합니다.';
      else if (g.movesLeft === 1 && E.inCheck(g.s, g.turn)) sub = '체크! 킹을 지키는 수만 둘 수 있습니다.';
    }
    st.appendChild(document.createTextNode(head));
    st.appendChild(el('small', '', sub || ''));
    $('turnno').textContent = `${g.turnNo}번째 턴`;
    const pips = $('pips');
    pips.textContent = '';
    if (g.phase === 'move') for (let k = 0; k < g.movesTotal; k++) pips.appendChild(el('i', k < g.movesTotal - g.movesLeft ? 'used' : ''));

    const deck = $('deck'), drawable = active && g.phase === 'draw';
    deck.textContent = '';
    deck.appendChild(el('b', '', String(g.deck.length)));
    deck.appendChild(document.createTextNode(drawable ? '뽑기' : '덱'));
    deck.disabled = !drawable;
    deck.classList.toggle('live', drawable);
    const drawn = $('drawn');
    drawn.textContent = '';
    if (!g.cards.length) drawn.appendChild(el('div', 'empty-card'));
    g.cards.forEach((k) => { const c = cardEl(k); if (shownSeq !== g.cardSeq) c.classList.add('new'); drawn.appendChild(c); });
    shownSeq = g.cardSeq;
    const wp = $('wildpick');
    wp.hidden = !(active && g.phase === 'wild');
    if (!wp.childElementCount) for (let n = 1; n <= 4; n++) wp.appendChild(btn('', String(n), () => act({ t: 'wild', n })));

    const log = $('log');
    log.textContent = '';
    g.log.slice(-150).forEach((l) => log.appendChild(el('div', l.cls, l.text)));
    log.scrollTop = log.scrollHeight;

    const ov = $('overlay');
    ov.textContent = '';
    ov.hidden = true;
    if (pendingPromo) {
      const d = el('div', 'dialog');
      d.appendChild(el('p', '', '어떤 기물로 승격할까요?'));
      const row = el('div', 'promo');
      for (const t of 'qrbn') {
        const m = pendingPromo;
        const b = btn(`pc ${g.turn}${t}`, null, () => act({ t: 'move', from: m.from, to: m.to, promo: t }));
        b.setAttribute('aria-label', { q: '퀸', r: '룩', b: '비숍', n: '나이트' }[t]);
        row.appendChild(b);
      }
      d.appendChild(row); ov.appendChild(d); ov.hidden = false;
    } else if (over) {
      const d = el('div', 'dialog');
      d.appendChild(el('h2', '', net && mine ? (g.winner === mine ? '이겼습니다' : '졌습니다') : `${E.NAME[g.winner]} 승리`));
      d.appendChild(el('p', '', g.winReason));
      if (!net || mine) d.appendChild(btn('btn primary', net ? '다시 하기 (색 바꿈)' : '다시 하기', () => act({ t: 'new' })));
      ov.appendChild(d); ov.hidden = false;
    }
    renderNet();
  }

  function clickSq(i) {
    if (!canAct() || g.phase !== 'move') return;
    const t = targets.find((m) => m.to === i);
    if (t) { if (t.promo) { pendingPromo = t; render(); } else act({ t: 'move', from: t.from, to: t.to }); return; }
    const p = g.s.b[i];
    if (p && p[0] === g.turn && sel !== i) { sel = i; targets = E.legalFrom(g, i); }
    else { sel = null; targets = []; }
    render();
  }

  $('deck').addEventListener('click', () => { if (canAct() && g.phase === 'draw') act({ t: 'draw' }); });
  $('newgame').addEventListener('click', () => act({ t: 'new' }));
  $('autoflip').addEventListener('change', () => { if (!$('autoflip').checked) flipped = false; render(); });
  $('mkroom').addEventListener('click', createRoom);
  window.addEventListener('hashchange', () => location.reload());

  // 시작: 주소에 #room-코드가 있으면 온라인, 없으면 한 화면에서 번갈아 두는 로컬 대전
  const code = ONLINE ? roomOfHash() : null;
  if (code && store.get('cu-room-' + code)) hostRoom(code);
  else if (code) joinRoom(code);
  else g = E.newGame();
  render();
})();

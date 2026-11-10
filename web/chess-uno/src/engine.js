// Chess Uno 규칙 엔진 (UI와 분리된 순수 로직). 브라우저와 Node 양쪽에서 동작.
// 보드: 길이 64 배열, index = row*8 + file, row 0 = 8랭크. 기물: 'wp','bk' 등 (색+종류).
const ChessUno = (() => {
  const opp = (c) => (c === 'w' ? 'b' : 'w');
  const sqName = (i) => 'abcdefgh'[i % 8] + (8 - (i >> 3));

  function initialChess() {
    const back = ['r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'];
    const b = new Array(64).fill(null);
    for (let f = 0; f < 8; f++) {
      b[f] = 'b' + back[f];
      b[8 + f] = 'bp';
      b[48 + f] = 'wp';
      b[56 + f] = 'w' + back[f];
    }
    return { b, castle: { wK: true, wQ: true, bK: true, bQ: true }, ep: null, epColor: null };
  }

  const cloneChess = (s) => ({ b: s.b.slice(), castle: { ...s.castle }, ep: s.ep, epColor: s.epColor });

  const KNIGHT = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];
  const KING = [[-1, -1], [-1, 0], [-1, 1], [0, -1], [0, 1], [1, -1], [1, 0], [1, 1]];
  const DIAG = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
  const ORTH = [[-1, 0], [1, 0], [0, -1], [0, 1]];
  const on = (r, f) => r >= 0 && r < 8 && f >= 0 && f < 8;

  // sq 칸이 by 색 기물에게 공격받는지
  function attacked(b, sq, by) {
    const r = sq >> 3, f = sq % 8;
    const pr = by === 'w' ? r + 1 : r - 1; // 공격하는 폰이 있을 행
    for (const df of [-1, 1]) if (on(pr, f + df) && b[pr * 8 + f + df] === by + 'p') return true;
    for (const [dr, df] of KNIGHT) if (on(r + dr, f + df) && b[(r + dr) * 8 + f + df] === by + 'n') return true;
    for (const [dr, df] of KING) if (on(r + dr, f + df) && b[(r + dr) * 8 + f + df] === by + 'k') return true;
    const ray = (dirs, types) => {
      for (const [dr, df] of dirs) {
        let rr = r + dr, ff = f + df;
        while (on(rr, ff)) {
          const p = b[rr * 8 + ff];
          if (p) { if (p[0] === by && types.includes(p[1])) return true; break; }
          rr += dr; ff += df;
        }
      }
      return false;
    };
    return ray(DIAG, 'bq') || ray(ORTH, 'rq');
  }

  const kingSq = (b, c) => b.indexOf(c + 'k');
  const inCheck = (s, c) => { const k = kingSq(s.b, c); return k >= 0 && attacked(s.b, k, opp(c)); };

  // 의사합법수(pseudo-legal move): 자기 킹 안전을 따지지 않은 기물 이동 규칙상의 수
  function pseudo(s, from) {
    const b = s.b, p = b[from];
    if (!p) return [];
    const c = p[0], t = p[1], r = from >> 3, f = from % 8, out = [];
    const add = (to, flag) => out.push({ from, to, flag: flag || null, promo: t === 'p' && (to >> 3 === 0 || to >> 3 === 7) });
    const step = (dirs) => { for (const [dr, df] of dirs) { const rr = r + dr, ff = f + df; if (on(rr, ff)) { const q = b[rr * 8 + ff]; if (!q || q[0] !== c) add(rr * 8 + ff); } } };
    const slide = (dirs) => { for (const [dr, df] of dirs) { let rr = r + dr, ff = f + df; while (on(rr, ff)) { const q = b[rr * 8 + ff]; if (!q) add(rr * 8 + ff); else { if (q[0] !== c) add(rr * 8 + ff); break; } rr += dr; ff += df; } } };
    if (t === 'p') {
      const d = c === 'w' ? -1 : 1, start = c === 'w' ? 6 : 1;
      if (on(r + d, f) && !b[(r + d) * 8 + f]) {
        add((r + d) * 8 + f);
        if (r === start && !b[(r + 2 * d) * 8 + f]) add((r + 2 * d) * 8 + f, 'dp');
      }
      for (const df of [-1, 1]) {
        if (!on(r + d, f + df)) continue;
        const to = (r + d) * 8 + f + df, q = b[to];
        if (q && q[0] !== c) add(to);
        else if (!q && s.ep === to && s.epColor !== c) add(to, 'ep');
      }
    } else if (t === 'n') step(KNIGHT);
    else if (t === 'b') slide(DIAG);
    else if (t === 'r') slide(ORTH);
    else if (t === 'q') { slide(DIAG); slide(ORTH); }
    else if (t === 'k') {
      step(KING);
      const home = c === 'w' ? 60 : 4, o = opp(c);
      if (from === home && !attacked(b, home, o)) {
        if (s.castle[c + 'K'] && b[home + 3] === c + 'r' && !b[home + 1] && !b[home + 2] &&
          !attacked(b, home + 1, o) && !attacked(b, home + 2, o)) add(home + 2, 'ck');
        if (s.castle[c + 'Q'] && b[home - 4] === c + 'r' && !b[home - 1] && !b[home - 2] && !b[home - 3] &&
          !attacked(b, home - 1, o) && !attacked(b, home - 2, o)) add(home - 2, 'cq');
      }
    }
    return out;
  }

  // 수를 보드에 적용. 잡힌 기물(없으면 null) 반환
  function applyMove(s, m, promoType) {
    const b = s.b, p = b[m.from], c = p[0];
    let cap = b[m.to];
    b[m.to] = p; b[m.from] = null;
    if (m.flag === 'ep') { const v = m.to + (c === 'w' ? 8 : -8); cap = b[v]; b[v] = null; }
    if (m.flag === 'ck') { b[m.to - 1] = b[m.to + 1]; b[m.to + 1] = null; }
    if (m.flag === 'cq') { b[m.to + 1] = b[m.to - 2]; b[m.to - 2] = null; }
    if (m.promo) b[m.to] = c + (promoType || 'q');
    if (p[1] === 'k') { s.castle[c + 'K'] = false; s.castle[c + 'Q'] = false; }
    for (const sq of [m.from, m.to]) {
      if (sq === 63) s.castle.wK = false; if (sq === 56) s.castle.wQ = false;
      if (sq === 7) s.castle.bK = false; if (sq === 0) s.castle.bQ = false;
    }
    if (m.flag === 'dp') { s.ep = (m.from + m.to) / 2; s.epColor = c; } else { s.ep = null; s.epColor = null; }
    return cap;
  }

  // strict=true: 수를 둔 뒤 내 킹이 공격받지 않아야 함 (턴의 마지막 수에 적용). 상대 킹을 잡는 수는 항상 허용.
  function movesFrom(s, from, strict) {
    const ms = pseudo(s, from);
    if (!strict) return ms;
    const c = s.b[from][0];
    return ms.filter((m) => {
      if (s.b[m.to] === opp(c) + 'k') return true;
      const t = cloneChess(s);
      applyMove(t, m, 'q');
      return !inCheck(t, c);
    });
  }

  function hasMove(s, c, strict) {
    for (let i = 0; i < 64; i++) if (s.b[i] && s.b[i][0] === c && movesFrom(s, i, strict).length) return true;
    return false;
  }

  // ---------- 카드 덱 ----------
  // 표준 108장 구성: 색 4종 × (0 한 장, 1~9 두 장씩, Skip·Reverse·Draw Two 두 장씩) + Wild 4 + Wild Draw Four 4
  function buildDeck(rng) {
    const d = [];
    for (const color of ['red', 'yellow', 'green', 'blue']) {
      d.push({ kind: 'num', n: 0, color });
      for (let n = 1; n <= 9; n++) { d.push({ kind: 'num', n, color }); d.push({ kind: 'num', n, color }); }
      for (const kind of ['skip', 'reverse', 'draw2']) { d.push({ kind, color }); d.push({ kind, color }); }
    }
    for (let i = 0; i < 4; i++) { d.push({ kind: 'wild', color: 'wild' }); d.push({ kind: 'wild4', color: 'wild' }); }
    for (let i = d.length - 1; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [d[i], d[j]] = [d[j], d[i]]; }
    return d;
  }

  // ---------- 게임 진행 ----------
  // phase: 'draw'(카드 뽑기) | 'wild'(숫자 선택) | 'move'(수 두기) | 'over'
  function newGame(rng) {
    rng = rng || Math.random;
    return {
      s: initialChess(), turn: 'w', phase: 'draw', movesLeft: 0, movesTotal: 0,
      penalty: { w: false, b: false }, deck: buildDeck(rng), rng, cards: [], cardNote: '',
      last: null, hi: null, taken: { w: [], b: [] }, winner: null, winReason: '', log: [], turnNo: 1, cardSeq: 0,
    };
  }

  const NAME = { w: '백', b: '흑' };
  const say = (g, text, cls) => { g.log.push({ text, cls: cls || '', turn: g.turn }); if (g.log.length > 200) g.log.splice(0, g.log.length - 200); };

  function pop(g) {
    // g.rng는 함수라 JSON으로 직렬화(전송·저장)하면 사라진다. 그때는 Math.random을 쓴다.
    if (!g.deck.length) { g.deck = buildDeck(g.rng || Math.random); say(g, '덱을 다 써서 새로 섞었습니다.', 'sys'); }
    return g.deck.pop();
  }

  function endTurn(g) {
    g.turn = opp(g.turn); g.phase = 'draw'; g.movesLeft = 0; g.movesTotal = 0; g.turnNo++;
  }

  // 남은 수를 둘 수 있는지 점검. 마지막 수인데 킹을 지킬 방법이 없으면 패배(체크메이트).
  function checkStuck(g) {
    const c = g.turn;
    if (g.movesLeft === 1) {
      if (hasMove(g.s, c, true)) return;
      if (inCheck(g.s, c)) {
        g.phase = 'over'; g.winner = opp(c);
        g.winReason = `체크메이트 — ${NAME[c]}이 턴을 끝낼 때 킹을 지킬 방법이 없습니다`;
        say(g, g.winReason, 'win');
      } else { say(g, `${NAME[c]}: 둘 수 있는 수가 없어 턴을 넘깁니다.`, 'sys'); endTurn(g); }
    } else if (!hasMove(g.s, c, false)) { say(g, `${NAME[c]}: 둘 수 있는 수가 없어 턴을 넘깁니다.`, 'sys'); endTurn(g); }
  }

  function startMoves(g, n) {
    if (n === 0) { say(g, `${NAME[g.turn]}: 0 → 수를 두지 못하고 턴을 넘깁니다.`); endTurn(g); return; }
    g.movesLeft = n; g.movesTotal = n; g.phase = 'move';
    checkStuck(g);
  }

  function drawCard(g) {
    if (g.phase !== 'draw') return;
    const c = g.turn, o = opp(c);
    g.cardNote = ''; g.cardSeq++;
    if (g.penalty[c]) {
      // 벌칙: 숫자 카드 2장이 나올 때까지 뽑아 작은 쪽만 사용 (특수 카드는 버림)
      g.penalty[c] = false;
      const nums = []; let burned = 0;
      while (nums.length < 2) { const k = pop(g); if (k.kind === 'num') nums.push(k); else burned++; }
      const n = Math.min(nums[0].n, nums[1].n);
      g.cards = nums.map((k) => ({ ...k, dim: false }));
      const keep = nums[0].n <= nums[1].n ? 0 : 1; g.cards[1 - keep].dim = true;
      g.cardNote = `벌칙: ${nums[0].n}과 ${nums[1].n} 중 작은 ${n}` + (burned ? ` (특수 카드 ${burned}장 버림)` : '');
      say(g, `${NAME[c]} 벌칙 — ${nums[0].n}, ${nums[1].n} 중 작은 숫자 ${n}수`);
      startMoves(g, n);
      return;
    }
    const k = pop(g);
    g.cards = [k];
    if (k.kind === 'num') { if (k.n > 0) say(g, `${NAME[c]}: ${k.n} → ${k.n}수 연속`); startMoves(g, k.n); }
    else if (k.kind === 'skip') {
      g.cardNote = '상대 턴을 건너뜁니다. 바로 한 장 더 뽑으세요.';
      say(g, `${NAME[c]}: Skip → ${NAME[o]}의 턴을 건너뛰고 다시 뽑습니다.`);
      g.turnNo++;
    } else if (k.kind === 'reverse') {
      if (g.last && g.last.by === o) {
        g.s = g.last.snap.s; g.taken = g.last.snap.taken;
        g.hi = { from: g.last.to, to: g.last.from };
        g.cardNote = `${NAME[o]}의 마지막 수(${sqName(g.last.from)}→${sqName(g.last.to)})를 되돌렸습니다.`;
        say(g, `${NAME[c]}: Reverse → ${NAME[o]}의 마지막 수 ${sqName(g.last.from)}→${sqName(g.last.to)} 취소`);
        g.last = null;
      } else {
        g.cardNote = '되돌릴 상대의 직전 수가 없어 효과가 없습니다.';
        say(g, `${NAME[c]}: Reverse → 되돌릴 수가 없어 효과 없음`);
      }
      endTurn(g);
    } else if (k.kind === 'draw2') {
      g.penalty[o] = true;
      g.cardNote = `${NAME[o]}은 다음 턴에 2장 중 작은 숫자만 씁니다.`;
      say(g, `${NAME[c]}: Draw Two → ${NAME[o]}에게 벌칙`);
      endTurn(g);
    } else if (k.kind === 'wild') {
      g.phase = 'wild'; g.cardNote = '1~4 중 둘 수의 개수를 고르세요.';
    } else if (k.kind === 'wild4') {
      g.penalty[o] = true;
      g.cardNote = `4수를 두고, ${NAME[o]}은 다음 턴 벌칙을 받습니다.`;
      say(g, `${NAME[c]}: Wild Draw Four → 4수 연속 + ${NAME[o]}에게 벌칙`);
      startMoves(g, 4);
    }
  }

  function chooseWild(g, n) {
    if (g.phase !== 'wild' || n < 1 || n > 4) return;
    say(g, `${NAME[g.turn]}: Wild → ${n}수 선택`);
    g.cardNote = `Wild: ${n}수를 골랐습니다.`;
    startMoves(g, n);
  }

  // 현재 턴에서 from 기물이 둘 수 있는 수 (마지막 수에는 킹 안전 제한 적용)
  function legalFrom(g, from) {
    if (g.phase !== 'move') return [];
    const p = g.s.b[from];
    if (!p || p[0] !== g.turn) return [];
    return movesFrom(g.s, from, g.movesLeft === 1);
  }

  const PIECE = { p: '폰', n: '나이트', b: '비숍', r: '룩', q: '퀸', k: '킹' };

  function playMove(g, m, promoType) {
    if (g.phase !== 'move') return false;
    const ok = legalFrom(g, m.from).find((x) => x.to === m.to);
    if (!ok) return false;
    const c = g.turn, piece = g.s.b[m.from];
    g.last = { by: c, from: m.from, to: m.to, snap: { s: cloneChess(g.s), taken: { w: g.taken.w.slice(), b: g.taken.b.slice() } } };
    const cap = applyMove(g.s, ok, promoType);
    g.hi = { from: m.from, to: m.to };
    if (cap) g.taken[c].push(cap);
    let text = `${NAME[c]} ${PIECE[piece[1]]} ${sqName(m.from)}→${sqName(m.to)}`;
    if (ok.flag === 'ck' || ok.flag === 'cq') text += ' (캐슬링)';
    if (ok.flag === 'ep') text += ' (앙파상)';
    if (cap) text += ` ×${PIECE[cap[1]]}`;
    if (ok.promo) text += ` =${PIECE[promoType || 'q']}`;
    say(g, text, 'mv');
    if (cap && cap[1] === 'k') {
      g.phase = 'over'; g.winner = c; g.winReason = `${NAME[c]}이 킹을 잡았습니다`;
      say(g, g.winReason, 'win');
      return true;
    }
    g.movesLeft--;
    if (g.movesLeft === 0) endTurn(g); else checkStuck(g);
    return true;
  }

  return { newGame, drawCard, chooseWild, legalFrom, playMove, inCheck, kingSq, sqName, opp, NAME,
    _test: { initialChess, cloneChess, movesFrom, applyMove, hasMove, buildDeck } };
})();
if (typeof module !== 'undefined') module.exports = ChessUno;

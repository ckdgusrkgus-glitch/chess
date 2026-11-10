// 실행: npm i peer && node test/online.test.js
// 로컬 중계 서버(PeerServer)를 띄우고, 서로 다른 브라우저 2개(host·guest)로 온라인 한 판을 끝까지 둔다.
// 중간에 guest 새로고침, host 새로고침을 한 번씩 해서 이어지는지도 확인한다.
const { chromium } = require('playwright');
const { PeerServer } = require('peer');
const http = require('http');
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'));
const snap = (p) => p.evaluate(() => [...document.querySelectorAll('#board .sq')].map((s) => s.getAttribute('aria-label')).sort().join(',') + '|' + document.getElementById('turnno').textContent);

async function step(p) {
  const phase = await p.evaluate(() => {
    if (document.querySelector('.promo')) return 'promo';
    if (document.querySelector('#overlay .dialog')) return 'over';
    if (!document.getElementById('deck').disabled) return 'draw';
    if (!document.getElementById('wildpick').hidden) return 'wild';
    return document.querySelector('.sq.mine') ? 'move' : 'wait';
  });
  if (phase === 'draw') await p.click('#deck');
  else if (phase === 'wild') await p.click('#wildpick button:nth-child(2)');
  else if (phase === 'promo') await p.click('.promo button');
  else if (phase === 'move') {
    for (let t = 0; t < 40; t++) {
      const mine = await p.$$('.sq.mine');
      if (!mine.length) break;
      await mine[Math.floor(Math.random() * mine.length)].click();
      const can = await p.$$('.sq.can');
      if (can.length) { await can[Math.floor(Math.random() * can.length)].click(); break; }
    }
  }
  return phase;
}

(async () => {
  const peerServer = PeerServer({ host: '127.0.0.1', port: 9000, path: '/' });
  const web = http.createServer((q, r) => { r.setHeader('content-type', 'text/html; charset=utf-8'); r.end(html); }).listen(8080, '127.0.0.1');
  const b = await chromium.launch();
  const errs = [];
  const open = async () => {
    const ctx = await b.newContext({ viewport: { width: 1100, height: 800 } });
    await ctx.addInitScript(() => { window.CU_PEER_OPTS = { host: '127.0.0.1', port: 9000, path: '/', secure: false }; });
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errs.push(e.message));
    return p;
  };
  const host = await open(), guest = await open(), third = await open();
  await host.goto('http://127.0.0.1:8080/');
  await host.click('#mkroom');
  await host.waitForSelector('#roomlink');
  const link = await host.inputValue('#roomlink');
  console.log('방 링크:', link);
  await guest.goto(link);
  await guest.waitForFunction(() => /나는 흑/.test(document.getElementById('net').textContent), null, { timeout: 20000 });
  await host.waitForFunction(() => /상대가 접속/.test(document.getElementById('net').textContent), null, { timeout: 20000 });
  console.log('guest 접속 확인. guest 화면은 흑 시점:', await guest.evaluate(() => document.querySelector('#board .sq').getAttribute('aria-label')));

  // 세 번째 사람은 관전자가 되어야 한다
  await third.goto(link);
  await third.waitForFunction(() => /관전 중/.test(document.getElementById('net').textContent), null, { timeout: 20000 });

  const sync = async () => {
    const want = await snap(host).then((s) => s.split('|')[1]);
    await guest.waitForFunction((t) => document.getElementById('turnno').textContent === t && document.getElementById('net').textContent.includes('연결됨'), want, { timeout: 30000 });
  };
  let over = false, reloads = 0;
  for (let i = 0; i < 4000 && !over; i++) {
    const a = await step(host), c = await step(guest);
    over = a === 'over' && c === 'over';
    await host.waitForTimeout(15);
    if (i === 30) { await sync(); await guest.reload(); await sync(); reloads++; console.log('guest 새로고침 후 이어짐'); }
    if (i === 60) {
      const before = await snap(host);
      await host.reload();
      await host.waitForFunction(() => /상대가 접속/.test(document.getElementById('net').textContent), null, { timeout: 40000 });
      assert.strictEqual(await snap(host), before, 'host 새로고침 후 판이 달라짐');
      await sync(); reloads++; console.log('host 새로고침 후 이어짐');
    }
  }
  assert.ok(over, '게임이 끝나지 않음');
  const hs = await snap(host), gs = await snap(guest);
  assert.strictEqual(hs.split('|')[0], gs.split('|')[0], 'host와 guest의 보드가 다름');
  await third.waitForFunction(() => !!document.querySelector('#overlay .dialog'), null, { timeout: 10000 });
  console.log('host:', await host.textContent('#overlay .dialog'), '/ guest:', await guest.textContent('#overlay .dialog'), '/ 관전자:', await third.textContent('#overlay .dialog'));

  // 다시 하기: guest가 요청하면 색이 바뀐 새 판이 시작된다
  await guest.click('#overlay .btn');
  await guest.waitForFunction(() => /나는 백/.test(document.getElementById('net').textContent), null, { timeout: 10000 });
  console.log('다시 하기 → guest가 백. 새로고침', reloads, '회 통과. 오류:', errs);
  await b.close(); web.close(); process.exit(errs.length ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });

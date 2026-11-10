// 실행: node test/ui-smoke.js [스크린샷 저장 폴더]
// 실제 브라우저(Playwright)로 index.html을 열어 무작위로 한 판을 끝까지 클릭해 본다.
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const out = process.argv[2] || __dirname;
  const url = 'file://' + path.join(__dirname, '..', 'index.html');
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 800 } });
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message));
  p.on('console', (m) => m.type() === 'error' && errs.push(m.text()));
  await p.goto(url);
  let shotSel = false;
  for (let i = 0; i < 1500; i++) {
    const phase = await p.evaluate(() => {
      if (!document.getElementById('overlay').hidden) return document.querySelector('.promo') ? 'promo' : 'over';
      if (!document.getElementById('deck').disabled) return 'draw';
      if (!document.getElementById('wildpick').hidden) return 'wild';
      return 'move';
    });
    if (phase === 'over') break;
    if (phase === 'draw') await p.click('#deck');
    else if (phase === 'wild') await p.click('#wildpick button:nth-child(3)');
    else if (phase === 'promo') await p.click('.promo button');
    else {
      for (let t = 0; t < 40; t++) {
        const mine = await p.$$('.sq.mine');
        await mine[Math.floor(Math.random() * mine.length)].click();
        const can = await p.$$('.sq.can');
        if (can.length) {
          if (i > 10 && !shotSel) { shotSel = true; await p.screenshot({ path: path.join(out, 'sel.png') }); }
          await can[Math.floor(Math.random() * can.length)].click();
          break;
        }
      }
    }
  }
  await p.screenshot({ path: path.join(out, 'end.png') });
  console.log('status:', await p.textContent('#status'), '| errors:', errs);
  const m = await b.newPage({ viewport: { width: 400, height: 800 } });
  await m.goto(url);
  await m.click('#deck');
  console.log('mobile overflow:', await m.evaluate(() => document.documentElement.scrollWidth > innerWidth));
  await m.screenshot({ path: path.join(out, 'mobile.png'), fullPage: true });
  await b.close();
  if (errs.length) process.exit(1);
})();

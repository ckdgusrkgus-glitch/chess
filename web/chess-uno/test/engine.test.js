// 실행: node test/engine.test.js
const assert = require('assert');
const E = require('../src/engine.js');
const T = E._test;

// 1) perft: 표준 체스 합법수 개수 검증 (이동 규칙이 맞는지 확인하는 표준 기법)
function perft(s, c, d) {
  if (d === 0) return 1;
  let n = 0;
  for (let i = 0; i < 64; i++) {
    if (!s.b[i] || s.b[i][0] !== c) continue;
    for (const m of T.movesFrom(s, i, true)) {
      for (const pr of m.promo ? ['q', 'r', 'b', 'n'] : ['q']) {
        const t = T.cloneChess(s);
        T.applyMove(t, m, pr);
        n += perft(t, E.opp(c), d - 1);
      }
    }
  }
  return n;
}
const init = T.initialChess();
assert.strictEqual(perft(init, 'w', 1), 20);
assert.strictEqual(perft(init, 'w', 2), 400);
assert.strictEqual(perft(init, 'w', 3), 8902);
assert.strictEqual(perft(init, 'w', 4), 197281);

// Kiwipete: 캐슬링·앙파상·프로모션이 섞인 검증용 포지션
function fromFen(fen) {
  const [rows, , cs, ep] = fen.split(' ');
  const b = [];
  for (const ch of rows.replace(/\//g, '')) {
    if (/\d/.test(ch)) for (let i = 0; i < +ch; i++) b.push(null);
    else b.push((ch === ch.toUpperCase() ? 'w' : 'b') + ch.toLowerCase());
  }
  const s = { b, castle: { wK: cs.includes('K'), wQ: cs.includes('Q'), bK: cs.includes('k'), bQ: cs.includes('q') }, ep: null, epColor: null };
  if (ep !== '-') { s.ep = (8 - +ep[1]) * 8 + 'abcdefgh'.indexOf(ep[0]); s.epColor = ep[1] === '3' ? 'w' : 'b'; }
  return s;
}
const kiwi = fromFen('r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -');
assert.strictEqual(perft(kiwi, 'w', 1), 48);
assert.strictEqual(perft(kiwi, 'w', 2), 2039);
assert.strictEqual(perft(kiwi, 'w', 3), 97862);
const pos3 = fromFen('8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -');
assert.strictEqual(perft(pos3, 'w', 4), 43238);

// 2) 덱 구성
const deck = T.buildDeck(Math.random);
assert.strictEqual(deck.length, 108);
assert.strictEqual(deck.filter((k) => k.kind === 'num').length, 76);

// 3) 무작위 대국 2000판: 예외 없이 끝나는지, 불변식이 유지되는지
function rngOf(seed) { return () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; }; }
let wins = { w: 0, b: 0 }, unfinished = 0, mates = 0;
for (let seed = 1; seed <= 2000; seed++) {
  const rng = rngOf(seed);
  const g = E.newGame(rng);
  let steps = 0;
  while (g.phase !== 'over' && steps++ < 3000) {
    if (g.phase === 'draw') {
      const c = g.turn, hadPenalty = g.penalty[c];
      E.drawCard(g);
      if (hadPenalty) assert.strictEqual(g.cards.length, 2);
    } else if (g.phase === 'wild') E.chooseWild(g, 1 + Math.floor(rng() * 4));
    else {
      const c = g.turn, left = g.movesLeft, all = [];
      for (let i = 0; i < 64; i++) for (const m of E.legalFrom(g, i)) all.push(m);
      assert.ok(all.length > 0, 'move 단계인데 둘 수가 없음');
      const m = all[Math.floor(rng() * all.length)];
      assert.ok(E.playMove(g, m, 'qrbn'[Math.floor(rng() * 4)]));
      // 턴의 마지막 수를 둔 뒤에는 내 킹이 공격받지 않아야 한다
      if (left === 1 && g.phase !== 'over') assert.ok(!E.inCheck(g.s, c), '턴 종료 시 킹이 공격받음');
    }
    if (g.phase !== 'over') {
      assert.strictEqual(g.s.b.filter((p) => p === 'wk').length, 1);
      assert.strictEqual(g.s.b.filter((p) => p === 'bk').length, 1);
    }
  }
  if (g.phase === 'over') { wins[g.winner]++; if (g.winReason.startsWith('체크메이트')) mates++; } else unfinished++;
}
console.log('무작위 2000판:', wins, '체크메이트 종료', mates, '미종료', unfinished);

// 4) Reverse: 상대의 직전 수(잡힌 기물 포함)를 되돌린다
{
  const g = E.newGame(rngOf(7));
  g.deck = [{ kind: 'reverse', color: 'red' }, { kind: 'num', n: 1, color: 'red' }];
  E.drawCard(g);
  E.playMove(g, { from: 52, to: 36 }); // e2→e4
  assert.strictEqual(g.turn, 'b');
  E.drawCard(g); // Reverse
  assert.strictEqual(g.s.b[52], 'wp'); assert.strictEqual(g.s.b[36], null);
  assert.strictEqual(g.turn, 'w');
}
// 5) Skip: 같은 사람이 다시 뽑는다 / Draw Two: 상대 벌칙
{
  const g = E.newGame(rngOf(9));
  g.deck = [{ kind: 'num', n: 9, color: 'red' }, { kind: 'num', n: 2, color: 'red' }, { kind: 'skip', color: 'blue' }, { kind: 'draw2', color: 'red' }, { kind: 'skip', color: 'red' }];
  E.drawCard(g); assert.strictEqual(g.turn, 'w'); assert.strictEqual(g.phase, 'draw');
  E.drawCard(g); assert.strictEqual(g.turn, 'b'); assert.ok(g.penalty.b);
  E.drawCard(g); assert.strictEqual(g.movesLeft, 2); assert.ok(!g.penalty.b);
}
console.log('모든 테스트 통과');

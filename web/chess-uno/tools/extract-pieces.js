// 실행: node tools/extract-pieces.js <chessground.cburnett.css 경로>
// chessground 패키지에 들어 있는 cburnett 기물 SVG(data URI)를 이 프로젝트의 클래스명(.pc.wp 등)으로 바꿔 src/pieces.css에 저장한다.
const fs = require('fs');
const path = require('path');
const T = { pawn: 'p', bishop: 'b', knight: 'n', rook: 'r', queen: 'q', king: 'k' };
let css = fs.readFileSync(process.argv[2], 'utf8');
css = css.replace(/\.cg-wrap piece\.(\w+)\.(white|black)/g, (_, t, c) => '.pc.' + c[0] + T[t]);
css = css.replace(/\/\*\*[\s\S]*?\*\//, '/* 기물 SVG: Colin M.L. Burnett (cburnett), CC BY-SA 3.0 */');
fs.writeFileSync(path.join(__dirname, '..', 'src', 'pieces.css'), css);
console.log('wrote src/pieces.css');

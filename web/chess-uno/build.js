// 실행: node build.js  →  index.html(단일 파일 정적 사이트) 생성
// src/page.html 안의 /*PIECES*/, /*PEERJS*/, /*ENGINE*/, /*UI*/ 자리에 각 파일 내용을 끼워 넣는다.
const fs = require('fs');
const path = require('path');
const read = (f) => fs.readFileSync(path.join(__dirname, f), 'utf8');

function build(online) {
  return read('src/page.html')
    .replace('/*PIECES*/', () => read('src/pieces.css'))
    // PeerJS: 브라우저끼리 직접 연결(WebRTC)하게 해 주는 라이브러리. 외부 CDN에 의존하지 않도록 파일에 함께 넣는다.
    .replace('/*PEERJS*/', () => (online ? read('vendor/peerjs.min.js').replace(/\/\/# sourceMappingURL=.*/, '') : 'window.CU_ONLINE = false;'))
    .replace('/*ENGINE*/', () => read('src/engine.js'))
    .replace('/*UI*/', () => read('src/ui.js'));
}

// fragment.html: <html>/<body> 래퍼가 없고 온라인 기능을 뺀 본문 (WebRTC가 막힌 미리보기 환경용)
fs.writeFileSync(path.join(__dirname, 'fragment.html'), build(false));
fs.writeFileSync(
  path.join(__dirname, 'index.html'),
  `<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n</head>\n<body style="margin:0">\n${build(true)}</body>\n</html>\n`
);
console.log('built index.html', fs.statSync(path.join(__dirname, 'index.html')).size, 'bytes');

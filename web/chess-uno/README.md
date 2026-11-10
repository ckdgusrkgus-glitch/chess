# Chess Uno (웹)

체스에 우노 카드를 얹은 2인 대전 게임. 한 화면에서 번갈아 두는 로컬 대전(hot-seat)과, 링크를 보내 다른 PC와 두는 온라인 대전을 지원한다.

- 실행: `index.html`을 브라우저로 열면 끝. 서버·설치 불필요 (정적 사이트라 GitHub Pages에 그대로 올릴 수 있음).
- 빌드: `node build.js` → `src/page.html` + `src/engine.js` + `src/pieces.css`를 `index.html` 한 파일로 합침.
- 테스트: `node test/engine.test.js` (규칙 엔진), `node test/ui-smoke.js` (Playwright로 브라우저 한 판 자동 플레이), `npm i peer && node test/online.test.js` (브라우저 2개로 온라인 한 판).

## 온라인 대전 구조
- "온라인 방 만들기"를 누르면 주소 뒤에 `#room-코드`가 붙는다. 이 링크를 연 사람이 상대(흑)가 되고, 세 번째부터는 관전자다.
- 통신은 WebRTC(브라우저끼리 직접 연결하는 기술)를 PeerJS 라이브러리로 쓴다. 처음 서로를 찾을 때만 PeerJS 공개 중계 서버(0.peerjs.com)를 거치므로 별도 서버가 필요 없다.
- 방을 만든 쪽(host)이 게임 상태의 원본을 갖는다. 상대는 행동(카드 뽑기, 수 두기)만 보내고 host가 검증해 적용한 뒤 전체 상태를 내려준다(host-authoritative 방식).
- host는 상태를 브라우저 localStorage에 저장하므로 새로고침해도 판이 이어진다. host가 브라우저를 닫으면 대국은 멈춘다.
- `file://`로 연 경우에도 방은 만들어지지만, 링크를 남에게 보내려면 GitHub Pages 같은 공개 주소에 올려야 한다.

## 규칙 (A안)
| 카드 | 효과 |
|---|---|
| 0~9 | 숫자만큼 연속으로 수를 둠. 0은 턴 넘김 |
| Skip | 상대 턴 건너뜀, 바로 다시 뽑음 |
| Reverse | 상대의 직전 수를 되돌림. 내 턴 종료 |
| Draw Two | 상대는 다음 턴 2장 중 작은 숫자만 사용. 내 턴 종료 |
| Wild | 1~4 중 선택 |
| Wild Draw Four | 4수 + 상대 벌칙 |

- 킹을 잡으면 승리. 턴의 마지막 수를 둔 뒤에는 내 킹이 안전해야 하며, 그럴 방법이 없으면 패배.
- 벌칙 턴은 숫자 카드 2장이 나올 때까지 뽑음(특수 카드는 버림).
- Reverse는 상대의 수가 보드에서 가장 최근 수일 때만 동작.
- 카드 색은 효과 없음.

기물 그림: Colin M.L. Burnett (CC BY-SA 3.0), chessground 패키지에서 추출. `vendor/peerjs.min.js`: PeerJS 1.5.4 (MIT).

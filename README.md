# Kotlin Chess

콘솔에서 두 명이 번갈아 두는 체스 게임입니다. Kotlin + Gradle로 작성했습니다.

## 실행 방법

```bash
./gradlew run
```

## 조작법

- 이동: `e2e4` (from-square + to-square)
- 프로모션: `e7e8q` (승진할 기물: `q`=퀸, `r`=룩, `b`=비숍, `n`=나이트, 생략 시 퀸)
- 특정 칸의 가능한 수 보기: `moves e2`
- 기권: `resign`
- 종료: `quit`

## 지원 규칙

- 모든 기물의 이동/포획 규칙
- 캐슬링 (킹사이드/퀸사이드, 경유 칸 체크 여부 검사)
- 앙파상
- 폰 프로모션
- 체크/체크메이트/스테일메이트 판정
- 50수 규칙, 기물 부족에 의한 무승부 판정

## 프로젝트 구조

```
src/main/kotlin/chess/
  Piece.kt          - 기물 종류/색상
  Square.kt         - 좌표 표현 및 변환
  Move.kt           - 이동 표현
  Board.kt          - 보드 상태, 이동 적용, 공격 여부 판정
  MoveGenerator.kt  - 합법적인 이동 생성
  ChessGame.kt      - 게임 상태(체크메이트/스테일메이트 등) 및 상위 API
  Main.kt           - 콘솔 UI

src/test/kotlin/chess/
  MoveGeneratorTest.kt - perft 검증(초기 위치 depth 1~4), 캐슬링/앙파상/프로모션/스테일메이트 테스트
```

## 테스트

```bash
./gradlew test
```

이동 생성기의 정확성은 [perft](https://www.chessprogramming.org/Perft_Results) (초기 위치 기준 depth 1~4: 20, 400, 8902, 197281) 로 검증했습니다.

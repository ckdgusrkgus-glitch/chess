# Kotlin Chess (Android)

Kotlin으로 작성한 안드로이드 체스 앱입니다. 한 기기에서 두 명이 번갈아 두는 2인 대전과, 레이팅별로 난이도가 다른 AI와의 대결을 지원합니다.

## 실행 방법

1. Android Studio에서 이 폴더(`chess`)를 프로젝트로 엽니다. (`File > Open`)
2. Gradle 동기화가 끝날 때까지 기다립니다. (최초 1회, AGP/의존성 다운로드로 시간이 걸릴 수 있습니다)
3. 에뮬레이터 또는 실제 기기를 연결하고 Run ▶ 버튼을 누릅니다.

> Gradle 동기화 중 "Android Gradle Plugin을 업그레이드하시겠습니까?" 같은 안내가 뜨면 그대로 승인하셔도 됩니다. 이 저장소는 네트워크가 제한된 빌드 환경에서 작성되어 실제 Android SDK로 최종 빌드 검증을 하지 못했으니, Gradle 동기화나 빌드에서 에러가 나면 에러 메시지를 알려주세요.

## 화면 구성

- **시작 화면** (`MainActivity` / `activity_main.xml`): "2인 대전", "AI와 대결", "종료" 버튼. "AI와 대결"을 누르면 난이도 선택 다이얼로그가 뜹니다.
- **게임 화면** (`GameActivity` / `activity_game.xml`): 체스 보드 + 차례/체크 표시 + "기권", "새 게임" 버튼

## AI 대결

- 난이도 4단계, 레이팅으로 구분됩니다: 초급(800) · 중급(1200) · 상급(1600) · 최상급(2000)
- 사람은 항상 백, AI는 항상 흑입니다.
- AI는 미니맥스 + 알파베타 가지치기 탐색 엔진(`chess.ai.ChessAi`)으로, 기물 가치 + 기물별 위치 테이블(piece-square table)로 평가하고, 교환 수만 더 파고드는 quiescence search로 "마지막 수에서 퀸을 공짜로 내주는" 식의 얕은 수읽기 실수를 줄였습니다.
  - 난이도가 낮을수록 탐색 깊이가 얕고, 확률적으로 무작위 수를 두거나(블런더) 최선수 대신 상위 후보 중 하나를 무작위로 선택해 "사람다운" 약점을 흉내냅니다.
  - 최상급은 깊이 4 + quiescence, 블런더 없음, 항상 최선수만 선택합니다.
- AI 연산은 백그라운드 스레드에서 실행되어 UI가 멈추지 않으며, 계산 중에는 보드 입력이 잠기고 "AI가 생각 중…" 문구가 표시됩니다.

## 보드 조작

- 내 차례의 기물을 탭하면 이동 가능한 칸이 표시됩니다.
  - **점(dot)**: 이동하면 합법인 빈 칸
  - **원(ring)**: 이동하면 합법인 상대 기물 포획 칸
  - **X 표시**: 기물이 규칙상 갈 수는 있지만 이동하면 자신의 킹이 체크에 걸려 실제로는 둘 수 없는 칸
- 프로모션(폰이 마지막 줄 도달) 시 승진할 기물을 선택하는 다이얼로그가 뜹니다.
- 디자인은 chess.com의 초록/베이지 보드 색상 구성을 참고했습니다.

## 지원 규칙

모든 기물의 이동/포획, 캐슬링(경유 칸 체크 여부 포함), 앙파상, 폰 프로모션, 체크/체크메이트/스테일메이트, 50수 규칙 및 기물 부족 무승부까지 정식 체스 규칙을 구현했습니다.

## 프로젝트 구조

```
app/src/main/java/chess/
  Piece.kt, Square.kt, Move.kt, Board.kt, MoveGenerator.kt, ChessGame.kt
                          - 순수 Kotlin 체스 엔진 (Android 의존성 없음)
  ai/AiLevel.kt           - 난이도(레이팅/탐색 깊이/블런더 확률) 정의
  ai/Evaluator.kt         - 기물 가치 + 기물별 위치 테이블 평가 함수
  ai/ChessAi.kt           - 미니맥스 + 알파베타 + quiescence 탐색 엔진
  ui/MainActivity.kt      - 시작 화면, AI 난이도 선택
  ui/GameActivity.kt      - 게임 화면, AI 수 계산 트리거, 체크메이트/승진 다이얼로그 처리
  ui/ChessBoardView.kt    - 보드를 직접 그리고 터치 입력을 처리하는 커스텀 View

app/src/main/res/        - 레이아웃 XML, 색상/문자열 리소스

app/src/test/java/chess/
  MoveGeneratorTest.kt    - perft(초기 위치 depth 1~4: 20/400/8902/197281) 및 캐슬링/앙파상/프로모션/스테일메이트 단위 테스트
  ai/ChessAiTest.kt       - AI가 항상 합법수를 두는지, 외통 기회를 놓치지 않는지 검증
```

## 테스트

```bash
./gradlew testDebugUnitTest
```

체스 엔진 자체는 Android에 의존하지 않는 순수 Kotlin이라, 로직 변경 시 이 테스트만으로도 규칙 정확성을 검증할 수 있습니다.

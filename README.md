# Kotlin Chess (Android)

Kotlin으로 작성한 안드로이드 체스 앱입니다. 시작 화면에서 게임을 시작하면 한 기기에서 두 명이 번갈아 두는 오프라인 체스 보드가 나옵니다.

## 실행 방법

1. Android Studio에서 이 폴더(`chess`)를 프로젝트로 엽니다. (`File > Open`)
2. Gradle 동기화가 끝날 때까지 기다립니다. (최초 1회, AGP/의존성 다운로드로 시간이 걸릴 수 있습니다)
3. 에뮬레이터 또는 실제 기기를 연결하고 Run ▶ 버튼을 누릅니다.

> Gradle 동기화 중 "Android Gradle Plugin을 업그레이드하시겠습니까?" 같은 안내가 뜨면 그대로 승인하셔도 됩니다. 이 저장소는 네트워크가 제한된 빌드 환경에서 작성되어 실제 Android SDK로 최종 빌드 검증을 하지 못했으니, Gradle 동기화나 빌드에서 에러가 나면 에러 메시지를 알려주세요.

## 화면 구성

- **시작 화면** (`MainActivity` / `activity_main.xml`): "게임 시작", "종료" 버튼
- **게임 화면** (`GameActivity` / `activity_game.xml`): 체스 보드 + 차례/체크 표시 + "기권", "새 게임" 버튼

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
  ui/MainActivity.kt      - 시작 화면
  ui/GameActivity.kt      - 게임 화면, 체크메이트/승진 다이얼로그 처리
  ui/ChessBoardView.kt    - 보드를 직접 그리고 터치 입력을 처리하는 커스텀 View

app/src/main/res/        - 레이아웃 XML, 색상/문자열 리소스

app/src/test/java/chess/
  MoveGeneratorTest.kt    - perft(초기 위치 depth 1~4: 20/400/8902/197281) 및 캐슬링/앙파상/프로모션/스테일메이트 단위 테스트
```

## 테스트

```bash
./gradlew testDebugUnitTest
```

체스 엔진 자체는 Android에 의존하지 않는 순수 Kotlin이라, 로직 변경 시 이 테스트만으로도 규칙 정확성을 검증할 수 있습니다.

# 증강체스 시스템 설계 문서

> 이 문서는 코드가 아니라 **설계안**이다. "증강체스"(잉체스 고안, salivapep 개발,
> augmentchess.org)의 증강 드래프트 시스템을 이 저장소의 체스 엔진 위에 어떻게
> 얹을지에 대한 아키텍처와 로드맵을 정리한다. 실제 구현은 여기서 정한 우선순위에
> 따라 이후 별도 작업으로 진행한다.

## 0. 참고 자료 범위

나무위키 "증강체스" 문서군에서 확보한 자료를 기준으로 작성했다:

- 기물 문서 (기본/변형/오리지널/기타 기물 약 60여 종, 기보 표기와 기물 가치 포함)
- 능력 문서 (가호/보호/HP/중첩/관측/마나/회피/고스트/잠복 등 15개 공용 능력)
- 오프닝 증강 36개, 미들 증강 약 79개, 엔드 증강 약 55개, 규칙 증강 27개, 기물 증강 33개
  (총 약 230개)

전체 목록과 개별 설명은 대화 기록에만 있고 이 저장소에는 아직 옮겨두지 않았다.
1단계 구현에 들어갈 때 실제로 채택한 증강만 코드/문서로 옮기는 것을 권장한다
(230개 전체를 미리 옮겨 적는 것은 유지보수 비용만 키운다).

## 1. 왜 "기능 추가"가 아니라 "재설계"인가 — 현재 엔진의 전제

현재 엔진은 표준 체스 하나만 돌리도록 만들어져 있고, 그 전제가 코드 곳곳에
하드코딩되어 있다.

- **기물 종류가 닫혀 있다.** `PieceType`은 6개 값의 enum이고
  (`app/src/main/java/chess/Piece.kt:9`), 행마 로직은
  `MoveGenerator.pseudoLegalMoves`의 `when (piece.type)` 분기 하나로 전부
  처리된다(`app/src/main/java/chess/MoveGenerator.kt:7-15`). 새 기물 하나를
  추가하려면 이 `when`이 exhaustive해야 하므로 반드시 건드려야 한다.
- **기물 가치/평가표도 닫혀 있다.** `Evaluator.PIECE_VALUE`,
  `Evaluator.PST`는 `Map<PieceType, _>`이고 `getValue()`로 조회한다
  (`app/src/main/java/chess/ai/Evaluator.kt:28-112`). `PieceType`이
  하나라도 늘면 이 맵들도 같이 늘어야 하고, 새 기물의 포지션 테이블(PST)은
  누군가 새로 설계해야 한다.
- **승패 조건이 하드코딩된 한 가지뿐이다.** `ChessGame.status()`는
  체크메이트/스테일메이트/50수/기물 부족만 판정한다
  (`app/src/main/java/chess/ChessGame.kt:8-24`). 증강체스는 이미 기반 규칙부터
  다르다 — 킹이 잡혀야 종료, 스테일메이트=패배 — 여기에 더해 종교 승리, 하이랜더,
  민주주의, 왕관(CTF), 레이싱 킹, 더블 체크 등 **증강마다 별도의 승리/패배 조건을
  추가**한다. 지금 구조는 조건 하나를 늘릴 자리가 없다.
- **`Move`가 표현할 수 있는 것이 제한적이다.** `from/to/promotion/isEnPassant/
  isCastleKingSide/isCastleQueenSide` 뿐이다. 통나무의 자동 후속 이동, 국무총리·
  버서커의 "한 번의 수로 두 번 이동한 것처럼 보이는" 행마, 빅룩/거신병의 다중 기물
  포획, 포탈 이동 같은 것은 지금의 `Move` 하나로는 표현이 안 된다.
- **턴이 "한 번에 한 기물, 한 번 이동"을 전제한다.** 가속(턴당 2행동), 재배치/
  왕명(한 기물 2회 이동), 아이돌(연쇄 이동) 같은 증강은 턴 구조 자체를 건드린다.
- **보드가 8x8 고정, 기물 배치가 `setup()` 하나로 고정.** 붕괴(보드 축소), 컨베이어,
  대각선 체스, 960/344200, 호드, 런던 시스템 선(先)배치 등은 가변 보드/가변 초기
  배치를 요구한다.
- **AI(`ChessAi`)가 `Board`/`MoveGenerator`만 보고 순수 미니맥스를 돈다**
  (`app/src/main/java/chess/ai/ChessAi.kt`). 시한부 상태 효과, 자원(마나), 중립
  기물, 확률적 효과(주사위/실수/트릭스터) 같은 걸 넣으면 "점수를 정확히 매길 수
  있는 정적 국면"이라는 전제가 깨진다.
- **`GameRecord`가 수(move) 목록만 저장한다**(`app/src/main/java/chess/history/
  GameRecord.kt`). 어떤 증강이 언제 뽑혔는지 기록이 없으면 복기(`ReviewActivity`)
  화면에서 재현 자체가 불가능하다.

결론: 증강 몇 개를 if문으로 끼워 넣는 방식으로는 금방 한계에 부딪힌다. 아래는
이 전제들을 하나씩 일반화하는 방향의 설계다.

## 2. 아키텍처 개요

레이어를 다음과 같이 나눈다.

```
┌─────────────────────────────────────────────┐
│  UI (GameActivity, 드래프트 픽 화면, ReviewActivity) │
├─────────────────────────────────────────────┤
│  RuleSet / MatchState                        │  ← 이번 판에 적용된 규칙 + 증강 조합
│    - 활성 Augment 목록                         │
│    - 승패 조건 목록 (WinCondition[])            │
│    - 턴 구조 정책 (TurnPolicy)                  │
│    - 보드 형태 (BoardShape)                     │
├─────────────────────────────────────────────┤
│  Effect Engine (신규)                          │  ← 이벤트 훅 + 상태 효과
│    - GameEvent 발행/구독                        │
│    - StatusEffect (가호/보호/잠복/HP/마나 ...)    │
├─────────────────────────────────────────────┤
│  Core Engine (기존 + 일반화)                     │
│    - Board (기존 필드 + 확장 슬롯)                │
│    - PieceKind / MovementRule (신규, PieceType 대체) │
│    - MoveGenerator (신규 아키텍처: pluggable)      │
│    - ChessAi / Evaluator (pluggable 평가로 교체) │
└─────────────────────────────────────────────┘
```

핵심 원칙: **일반 체스는 "증강이 하나도 없는 RuleSet"이 되어야 한다.** 즉 지금의
`GameActivity`(일반 대국)는 리팩터링 후에도 동일하게 동작해야 하고, 증강체스는
같은 엔진 위에 RuleSet만 다르게 얹은 것이어야 한다. 별도 엔진을 새로 파는 것은
유지보수 두 벌을 만드는 셈이라 피한다.

## 3. 핵심 데이터 모델

### 3.1 PieceKind — `PieceType` enum을 대체

```kotlin
data class PieceKind(
    val id: String,                 // "QUEEN", "GRASSHOPPER", "AMAZON" ...
    val symbol: Char,                // 기보 표기 (문서의 "기보 표기" 컬럼과 동일)
    val baseValue: Int,              // 기물 가치(centipawn) — 평가용
    val movement: MovementRule,      // 행마 정의
    val isMajorPiece: Boolean = false,
    val boardFootprint: BoardFootprint = BoardFootprint.SINGLE, // 거신병/빅룩용 2x2 등
    val maxHp: Int = 1               // 빅룩/거신병처럼 여러 번 맞아야 파괴되는 기물
)
```

`PieceType` enum 6종은 `PieceKind` 6개의 사전 정의 인스턴스가 된다. 기존 코드가
`PieceType.QUEEN`을 참조하는 곳은 `PieceKind.QUEEN` 상수로 치환하면 되므로,
**일반 체스 경로는 동작이 바뀌지 않는다.**

### 3.2 MovementRule — 행마를 데이터로 표현

기물 행마를 크게 세 가지 원형으로 분해할 수 있다 (문서에 나온 60여 종 기물을
전수 분류해 보면 실제로 아래 패턴의 조합으로 대부분 커버된다):

```kotlin
sealed class MovementRule {
    /** 룩/비숍/퀸/구행(2연속)/전령(3칸+도약) 등: 방향 벡터로 미끄러지듯 이동 */
    data class Rider(
        val directions: List<Pair<Int, Int>>,
        val maxSteps: Int = 7,          // 전령=3, 낙타형 변형 등에 사용
        val canJumpOver: Boolean = false, // 프로테스탄트, 추기경(벽 튕김은 별도 처리)
        val repeatCount: Int = 1          // 구행=2 (룩 행마를 두 번 잇는다)
    ) : MovementRule()

    /** 나이트/낙타/알필/그래스호퍼(뛰어넘기 포함) 등: 고정 오프셋으로 한 번에 도약 */
    data class Leaper(val offsets: List<Pair<Int, Int>>) : MovementRule()

    /** 폰류: 전진/공격이 분리되고 방향성이 있는 기물 (베롤리나 폰=전환 증강 포함) */
    data class PawnLike(
        val advanceDirs: List<Pair<Int, Int>>,
        val captureDirs: List<Pair<Int, Int>>,
        val firstMoveDouble: Boolean = true,
        val promotesAt: (Color) -> Int
    ) : MovementRule()

    /** 킹/근위병/사신/괴물 등: 8방향 1칸. 캐슬링 여부는 플래그로 */
    data class KingLike(val canCastle: Boolean = false) : MovementRule()

    /** 아마존(퀸+나이트), 로얄나이트 등: 두 규칙의 합집합 */
    data class Union(val rules: List<MovementRule>) : MovementRule()

    /** 기물이 코드 없이 데이터만으로 표현 안 되는 특수 케이스용 탈출구
        (예: 마법사의 마법 시전, 트릭스터의 매턴 변신, 슬라임의 복제-이동) */
    data class Scripted(val handlerId: String) : MovementRule()
}
```

`MoveGenerator`는 지금처럼 `when (piece.type)`으로 분기하는 대신, 위 4가지
원형(Rider/Leaper/PawnLike/KingLike/Union)에 대한 **범용 생성 함수 4개**만 가지면
된다. `Scripted`는 문서상 정말 코드로만 표현 가능한 소수(마법사, 트릭스터, 슬라임,
드래곤의 자리 교환, 통나무의 자동 이동 등)를 위한 탈출구이고, `handlerId`로 별도
등록된 Kotlin 함수를 찾아 실행한다. 60여 종 기물 중 `Scripted`가 필요한 건
대략 10종 미만으로 추정된다 — 나머지는 데이터 정의만으로 끝난다.

### 3.3 Move 확장

```kotlin
data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceKind? = null,
    val isEnPassant: Boolean = false,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
    // --- 신규 ---
    val additionalCaptures: List<Square> = emptyList(), // 빅룩/거신병/공성추 다중 포획
    val swapWith: Square? = null,          // 드래곤, 교대, 치환
    val followUpAction: FollowUpAction? = null // 통나무 자동 롤, 국무총리 2단 이동
)
```

기존 `Move` 생성 코드는 전부 새 필드에 기본값이 있으므로 그대로 컴파일된다.

### 3.4 StatusEffect — 능력 문서의 15개 공용 효과

```kotlin
data class StatusEffect(
    val kind: StatusEffectKind,   // BLESSING(가호), PROTECTION(보호), STEALTH(잠복), ...
    val remainingTurns: Int? = null,  // null = 영구/조건부 해제
    val data: Map<String, Any> = emptyMap() // 카멜레온 변신 확률, 마나량 등
)
```

`Board`(또는 `Board`가 들고 있는 보조 맵)에 `Map<Square, List<StatusEffect>>`를
추가해 기물별 상태를 추적한다. HP는 `PieceKind.maxHp`와 별개로 "현재 HP"를
같은 맵에 들고 있어야 한다(빅룩이 이동해도 HP는 유지되므로 기물 인스턴스에
귀속되어야 하고, `Piece` 데이터 클래스에 `currentHp: Int = kind.maxHp` 필드를
추가하는 편이 `Board`의 보조 맵보다 단순하다).

### 3.5 Augment

```kotlin
data class Augment(
    val id: String,
    val displayName: String,
    val category: AugmentCategory,   // OPENING / MIDDLE / END / RULE / PIECE
    val cost: Float,                  // 별 개수 (0.5 단위)
    val isPassive: Boolean,           // 즉시/상시 발동 vs 플레이어가 액티브로 사용
    val hooks: List<GameHook>         // 아래 4장 참고
)
```

증강 하나가 "여러 종류의 훅을 동시에 등록"하는 경우가 많다 (예: 퀸즈 갬빗은
①퀸을 즉시 제거하고 ②두 폰에 영구 보호를 걸고 ③이후 그 폰이 잡히는 것을 막는
훅까지 필요 없음 — 보호 자체가 이미 "안 잡힘"을 의미하므로 훅 1개로 충분). 즉
`Augment`는 "훅의 묶음"이라는 뜻이지 각 훅을 새로 발명해야 한다는 뜻은 아니다.
아래 4장에서 정의하는 훅의 종류를 재조합하는 것만으로 상당수 증강을 커버할 수
있다.

## 4. 이벤트/훅 시스템

게임 루프에 다음 시점들을 훅으로 노출한다. (기존 코드에서 대응되는 지점을 함께
표시)

| 훅 | 현재 코드에서 삽입할 지점 | 용도 예시 |
|---|---|---|
| `onGameStart` | `ChessGame` 생성 시점 | 규칙 증강 랜덤 적용, 초기 배치 변형(런던/호드/960) |
| `onMoveGenerated(pseudoLegalMoves)` | `MoveGenerator.legalMoves` 이후 | 도발(후진 금지), 빙판(최대사거리 강제), 왕명(임시 행마 부여) |
| `onBeforeApplyMove(move)` | `Board.applyMove` 직전 | 실수(50%/20% 확률로 반대 기물 포획), 회피 판정 |
| `onAfterApplyMove(move, captured)` | `Board.applyMove` 직후 | 마나 획득(마법사), 가호 소모, 카멜레온 변신, 초월 승급 |
| `onTurnEnd` | 사이드 전환 직전 | 통나무 자동 이동, 강풍/최후통첩 타이머 감소, 상인 골드 획득 |
| `onWinConditionCheck` | `ChessGame.status()` 대체 | 종교 승리/하이랜더/민주주의/왕관/레이싱 킹 등 |
| `onDraftPoint` | 신규 — 오프닝(1회)/미들·엔드(주기적) | 증강 픽 UI 트리거 |

`GameHook`은 위 7종을 sealed interface로 두고, 각 `Augment`가 필요한 훅만
등록한다. 엔진의 메인 루프(`GameActivity`가 들고 있는 진행 로직)는 각 시점마다
"등록된 훅을 전부 순서대로 실행"하기만 하면 되므로, **증강을 추가할 때 엔진
루프 코드를 매번 고칠 필요가 없다** — 이게 이 설계의 핵심 목표다.

## 5. 승패 조건 일반화

```kotlin
fun interface WinCondition {
    fun check(state: MatchState): WinResult?  // null이면 아직 안 끝남
}
```

기본 규칙(증강체스 공통 기반)부터 이미 표준 체스와 다르다는 점을 짚어야 한다:

- 체크메이트로 즉시 끝나지 않음 — 킹은 공격받는 칸에 갈 수 있고, **킹이 실제로
  잡혀야** 종료.
- 스테일메이트 = 그 수를 둘 차례였던 쪽의 패배 (표준 체스의 무승부가 아님).

`ChessGame.status()`를 `List<WinCondition>`을 순회하는 구조로 바꾸고, 기본
2개(킹 포획, 스테일메이트=패배)를 항상 포함, 증강이 추가하는 조건(민주주의=
폰 전멸패, 종교승리=비숍差3, 하이랜더=기물 중복 없음, 레이싱킹=킹 반대편
도달, 왕관=10턴 점유, 더블체크=이중 체크 즉시, 청바지/캔슬링처럼 상태를
바꾸기만 하는 것 등)을 이 리스트에 얹는다.

## 6. 턴 구조 일반화

`TurnPolicy`로 "이번 턴에 몇 번의 행동이 가능한가"를 결정한다.

```kotlin
data class TurnPolicy(
    val actionsPerTurn: Int = 1,       // 가속=2
    val forcedFollowUps: List<FollowUpAction> = emptyList() // 통나무 등
)
```

재배치/왕명처럼 "이번 수에 한해 한 기물이 특수 행마"를 부여하는 것은 턴 구조가
아니라 3.3의 `MovementRule` 임시 오버라이드로 처리하는 편이 더 간단하다 — 이런
것까지 `TurnPolicy`에 넣으면 상태가 폭발한다. 원칙: **"누가 몇 번 두는가"는
TurnPolicy, "무엇을 할 수 있는가"는 MovementRule/훅** 으로 역할을 분리한다.

## 7. 보드 형태 일반화

```kotlin
data class BoardShape(
    val width: Int = 8,
    val height: Int = 8,
    val collapsedSquares: Set<Square> = emptySet(), // 붕괴 규칙 진행 상황
    val heightZones: Set<Square> = emptySet(),       // 고지전
    val portals: Map<Square, Square> = emptyMap()    // 포탈
)
```

초기 배치는 `setup()` 하나를 `List<(PieceKind, Color, Square)>`를 받는 함수로
바꾸고, 런던 시스템/호드/960/344200/대각선 체스/상인 조합처럼 배치 자체가
다른 증강들은 각자의 배치 리스트만 데이터로 정의하면 된다.

## 8. AI(`ChessAi`) 대응 — 현실적인 선 긋기가 필요하다

가장 위험 부담이 큰 부분이다. 솔직하게 짚어야 할 것들:

1. **평가 함수가 더 이상 "정적"이지 않다.** 가호 걸린 폰, HP가 남은 빅룩, 마나를
   모은 마법사는 같은 위치의 같은 기물이라도 가치가 다르다. `Evaluator`가
   `StatusEffect`/HP를 반영하도록 확장해야 하고, 새 `PieceKind`마다 기물
   가치를 새로 정해야 한다 (문서에 이미 대부분 값이 나와 있어 이 부분은 데이터
   이식만 하면 됨 — 예: 아마존 13점, 구행 15점, 곰 17점).
2. **탐색 트리 자체가 오염된다.** 확률적 효과(실수 20%, 트릭스터 랜덤 변신,
   빙결/파괴 주사위)가 걸리면 미니맥스는 기대값(expectimax)로 바꿔야 정확한데,
   지금 엔진은 순수 minimax + alpha-beta다(`ChessAi.search`). 확률 노드를
   섞으면 알파-베타 가지치기 전제가 깨지고 탐색 비용이 급증한다.
3. **결론: AI가 증강체스를 "완벽하게" 두게 만드는 건 이번 로드맵의 목표로
   잡지 않는 것을 권장한다.** 대신 다음 중 하나를 선택해야 한다:
   - (A) 1단계는 사람 vs 사람 로컬 대국에만 증강체스를 연다. `GameActivity`의
     AI 대국 경로는 기존 표준 체스만 유지.
   - (B) AI 대국에도 열되, AI의 탐색 깊이를 얕게 낮추고 확률적 효과는
     "기댓값을 무시하고 최선의 확정 결과만 본다"는 근사치로 평가한다 (정확하진
     않지만 크래시 없이 동작은 함).
   - (C) 증강 종류를 "AI가 이해 가능한 것"(Tier 1~2, 12장 참고)으로만 제한하고,
     Tier 3 이상(신규 기물, 확률/랜덤 계열)은 AI 대국에서 드래프트 풀에서 제외.

   이 문서에서는 **(C)를 시작점으로 권장**한다 — 사람 vs 사람에서는 전체
   증강을 다 열어주고, AI 대국에서만 드래프트 풀을 좁히는 방식이 사용자
   경험을 크게 해치지 않으면서 구현 위험을 줄인다.

## 9. `GameRecord` / 복기 화면에 미치는 영향

`GameRecord`에 다음을 추가해야 복기가 가능하다:

```kotlin
data class GameRecord(
    // 기존 필드 ...
    val ruleSetId: String = "standard",       // "standard" = 지금과 동일하게 동작
    val draftedAugments: List<AugmentPick> = emptyList() // 언제, 무엇을 뽑았는지
)
```

`ReviewActivity`/`MoveExplanationActivity`/`ChessAi` 기반 판단(수 품질, 커뮤니티
통계 등)은 전부 "표준 체스"를 전제로 하므로, `ruleSetId != "standard"`인 기록은
**복기의 AI 분석·수 품질 판정·커뮤니티 통계 기능을 자동으로 끄고 수순 재생만
제공**하는 것을 권장한다. 이 부분까지 증강체스에 맞춰 다시 만드는 것은 8장에서
말한 AI 평가 문제와 완전히 같은 문제라 별도 로드맵으로 미룬다.

## 10. 드래프트 UX 흐름 (개요)

- **오프닝 증강**: 게임 시작 전 1회, 후보 3장 중 1장 선택 → 즉시/1턴째 자동 적용.
- **미들/엔드 증강**: 게임 중 주기적으로(예: N수마다, 또는 기물 교환 등 특정
  이벤트 후) 드래프트 팝업 → 후보 3장 중 1장. "엔드" 카테고리는 후반부에 등장
  확률이 높아지는 가중치를 두는 정도로 충분하고, 오프닝처럼 엄격한 게이팅은
  필요 없어 보인다(원작 문서에도 "검은 상자"로 엔드 카드가 오프닝 단계에
  섞여 나올 수 있다는 언급이 있음).
- **규칙 증강**: 플레이어 선택 없음. 게임 시작 시 서버(혹은 로컬 RNG)가 1개
  랜덤 적용.
- **기물 증강**: 미들/엔드와 같은 드래프트 풀에 섞여 나오는 것으로 보이며,
  액티브(플레이어가 원하는 시점에 발동)인 것도 있고 즉시 발동(패시브)인 것도
  있다 — `Augment.isPassive`로 구분.

이 화면은 `GameActivity` turn loop에 `onDraftPoint` 훅이 걸릴 때마다 모달로
띄우는 형태가 가장 기존 구조와 잘 맞는다.

## 11. 난이도 티어 분류

전부를 한 번에 만들 수 없으므로, 아래 기준으로 우선순위를 정한다.

- **Tier 1 — 정적 규칙/배치 변경만 필요.** 새 `PieceKind`나 상태 효과 없이,
  기존 기물의 배치·프로모션 랭크·캐슬링 조건 같은 파라미터만 바꾸면 됨.
  예: 퀸의 기병대, 조기 진급, 성급한 승진, 최종병기, 존버, 부정출발, 재활용,
  960/344200 체스.
- **Tier 2 — 시한부/조건부 상태 효과(StatusEffect) 필요.** 새 기물은 필요
  없지만 "N턴 동안", "M번까지" 같은 타이머·카운터 상태를 붙여야 함.
  예: 가호, 보호, 회피, 마녀재판, 독이 든 폰, 무장해제, 빙결, 절단, 탈진.
- **Tier 3 — 새 `PieceKind` 1~2종, 그러나 `MovementRule` 원형(Rider/Leaper/
  PawnLike/Union)만으로 표현 가능.** 예: 알필, 낙타, 그래스호퍼, 아마존,
  로얄 나이트, 만, 페르즈, 프로테스탄트, 전령, 유니콘, 다바바 계열(알리바바).
- **Tier 4 — `Scripted` 핸들러(진짜 코드)가 필요한 기물/효과.** 예: 마법사
  (마나+주문), 트릭스터/키메라(매턴 변신), 슬라임(복제), 드래곤(자리 교환),
  곰(반격), 상인(골드/매수), 세이렌/선교사(전향), 통나무(자동 이동), 빅룩/
  거신병(HP+다중칸+다중포획).
- **Tier 5 — 승패 조건·보드 구조·턴 구조 자체를 바꿈, 또는 AI가 사실상
  이해 불가능한 확률/은닉 정보.** 예: 민주주의, 하이랜더, 붕괴, 컨베이어,
  포탈, 중첩/관측(양자역학), 은신/위장색(은닉 정보), 트롤리(비대칭 정보 UI
  자체가 필요).

## 12. 제안 로드맵

1. **0단계 (기반 공사, 코드 변경 없이도 시작 가능):** 이 문서에서 정한 데이터
   모델(`PieceKind`, `MovementRule`, `Move` 확장, `StatusEffect`, `WinCondition`,
   `TurnPolicy`, `BoardShape`)을 실제 타입으로 만들고, **기존 표준 체스가
   똑같이 동작하는지**를 회귀 테스트로 고정한다. 이 단계가 끝나기 전까지는
   증강을 단 하나도 넣지 않는다 — 리팩터링과 신규 기능을 같은 커밋에서
   섞지 않기 위함.
2. **1단계:** Tier 1 증강 5~10개 + 기본 승패 조건 변경(킹 포획, 스테일메이트=
   패배)만 있는 "증강체스 라이트" 모드를 사람 vs 사람으로 오픈.
3. **2단계:** `StatusEffect`/훅 엔진을 실제로 붙이고 Tier 2 증강을 추가.
4. **3단계:** `MovementRule` 조합으로 표현되는 Tier 3 기물 증강 몇 종 추가.
   이 시점에 `Evaluator`/`ChessAi`를 "새 PieceKind를 만나도 죽지 않게" 최소
   대응(기본값 폴백)까지만 해 둔다.
5. **4단계 이후:** Tier 4/5는 각각 별도 설계 논의가 필요한 규모이므로, 실제
   수요(사용자가 어떤 증강을 가장 원하는지)를 보고 개별적으로 진행한다. 이
   시점에 8장의 AI 전략(A/B/C)도 다시 확정한다.

## 13. 아직 결정 안 된 것들 (다음에 정해야 함)

- 미들/엔드 드래프트가 "몇 수마다" 뜨는지 원작 기준 확인 필요 (문서에 명확한
  주기가 안 나옴 — 실제 앱을 켜보거나 영상을 봐야 확인 가능해 보임).
- 카오스 모드/그랜드 모드(한 번에 2~3장 선택) 지원 여부 — 1단계 스코프에서는
  제외 권장.
- AI 대국에 증강체스를 언제부터 열지 (8장의 A/B/C 중 최종 선택).
- 기존 `GameActivity`/`ChessBoardView`가 "기물 1개 = 1칸"을 전제로 그림을
  그리는지 확인 필요 — 빅룩/거신병 같은 2x2 기물은 렌더링 쪽도 손봐야 한다
  (이 문서는 엔진 설계에 집중했고 `ChessBoardView` 렌더링 확장은 다루지 않았다).

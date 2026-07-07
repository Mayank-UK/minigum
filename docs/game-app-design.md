# minigum — Mini-Games App Design

Status: design (no game code yet). Decisions locked: **Compose** (no Godot/Go), **Ludo = local pass-and-play**.

## Goals
A single Android app hosting ~10 heterogeneous mini-games behind a shared shell:
home grid → per-game detail (how-to-play + leaderboard + Start) → play screen.
Optional online identity (Firebase Auth) and cross-device leaderboard (Firestore).
Reuses the existing `minigum` scaffold: Compose + Hilt + Retrofit + Room + `core.Result/guards` + dev/staging/prod flavors.

## Game catalog (fixed → define in code, not DB)
The catalog is static and version-controlled, so model it as a Kotlin catalog, not Firestore docs.

```kotlin
enum class RankingKind { HIGHER_BETTER, LOWER_BETTER, NONE }

data class GameDef(
    val id: String,                 // "snake"
    val title: String,              // "Snake"
    val icon: GameIcon,             // emoji or drawable ref
    val howToPlay: String,          // shown on detail screen
    val difficulty: Difficulty?,    // null if the game has no difficulty selector
    val ranking: RankingKind,       // how to order its leaderboard
    val scoreUnit: String,          // "points", "seconds", "moves", "—"
    val supportsOnlineScores: Boolean,
)
```
Games where `ranking == NONE` (sketching) don't post to the leaderboard.

Catalog (id → ranking / notes):
| id | ranking | scoreUnit | difficulty | notes |
|----|---------|-----------|-----------|-------|
| snake | HIGHER_BETTER | points | – | Canvas + game loop |
| flappy | HIGHER_BETTER | points | – | Canvas |
| shooting | HIGHER_BETTER | points | easy/normal/hard | Canvas, sprites + collision |
| sudoku | LOWER_BETTER | seconds | easy/medium/hard | generate+solve; UI is a grid |
| chess | NONE (win/lose/draw) | – | – | Kotlin chess engine; pass-and-play |
| ludo | NONE (placement) | – | – | pass-and-play on one device |
| sketch | NONE | – | – | freehand canvas; no leaderboard |
| node_logic | NONE (pass/fail) | – | – | drag/drop node graph; LLM checks |
| mic_camera | HIGHER_BETTER | points | – | CameraX + AudioRecord |
| api_pipeline | HIGHER_BETTER | points | – | trivia using an open API via Retrofit→Room→Repo |

## Data models

```kotlin
// Firebase Auth identity (Firestore: users/{uid})
data class AppUser(
    val id: String,            // Auth UID
    val displayName: String,   // chosen handle
    val createdAt: Long,
)

// Leaderboard entry (Firestore: scores collection; gameId field + composite index)
data class Score(
    val id: String,
    val gameId: String,
    val userId: String,
    val displayName: String,   // denormalized snapshot → leaderboard read needs no join
    val value: Long,           // numeric score (meaning set by GameDef.ranking/scoreUnit)
    val outcome: Outcome,      // WIN/LOSE/DRAW/INCOMPLETE/COMPLETE
    val difficulty: Difficulty?,
    val durationMs: Long,
    val createdAt: Long,
)
```
- **Username uniqueness:** enforce via a `usernames/{lowercase}` doc written in a Firestore transaction on register.
- **Denormalize `displayName`** in each `Score` so leaderboard queries are single-collection.
- **Leaderboard query:** `scores` ordered by `value` filtered by `gameId` (+ `difficulty`), composite index, paginated top-N.

## Auth + leaderboard model
- Guest (not signed in) → scores written to **Room only** (local leaderboard).
- Registered → scores written to **Firestore** (online), cached locally for offline.
- `LeaderboardRepository` decides destination from current auth state:
  `if (currentUser != null) remote.submit(...) else local.save(...)`.
- This is exactly the **"local now, online later"** pattern, applied per-user.

## Architecture → existing layers (the "API pipeline game" *is* this)

| Layer | Adds | Existing to reuse |
|-------|------|-------------------|
| `data/remote` | `AuthService` (FirebaseAuth), `LeaderboardRemoteDataSource`, `UsersRemoteDataSource` | Retrofit/OkHttp, `guardedApiCall` |
| `data/local` | `ScoreEntity` + `ScoreDao` | `AppDatabase`, `guardedDatabaseCall` |
| `data/repositoryimplementations` | `AuthRepositoryImpl`, `LeaderboardRepositoryImpl`, `UsersRepositoryImpl` | `safeRepositoryCall`, `ExceptionMapper`, `Result` |
| `domain` | `AuthRepository`, `LeaderboardRepository`, models, `SubmitScoreUseCase`, `GetTopScoresUseCase(gameId)`, `SignIn/Register/GetCurrentUser` use cases | `PostRepository` shape as template |
| `di` | `AuthModule`, `LeaderboardModule` | per-flavor `FlavorNetworkModule` (Firebase project per flavor) |
| `ui` | `HomeScreen` grid, `GameDetailScreen`, `PlayScreen`, `LeaderboardScreen`, `AuthScreen` | `NavRouter/NavGraph`, `PageScaffold`, theme |

Firebase projects map 1:1 onto the existing **dev/staging/prod** flavors (one `google-services.json` per flavor source set).

## Screen flow / navigation
Extend `NavRouter`:
- `home` — grid of `GameDef`s (exists).
- `detail/{gameId}` — how-to-play + leaderboard preview (top 5) + difficulty picker + **Start**.
- `play/{gameId}` — hosts the game composable; on finish → result/submit.
- `leaderboard/{gameId}` — full paginated board (optional if detail shows enough).
- `auth` / `profile` — sign-in/register, account.

`NavGraph` resolves `gameId` → `GameDef` from the catalog.

## Per-game contract (the key abstraction)
Keep the shell generic; a game only owes the shell **one thing**: emit a `GameResult` when it ends.

```kotlin
enum class Outcome { WIN, LOSE, DRAW, INCOMPLETE, COMPLETE }

data class GameResult(
    val outcome: Outcome,
    val score: Long?,        // null for non-scored games (chess win, sketch)
    val durationMs: Long,
    val difficulty: Difficulty?,
)

// A registry maps gameId → its play composable.
typealias GamePlay = @Composable GamePlayScope.() -> Unit

data class GamePlayScope(
    val gameId: String,
    val difficulty: Difficulty?,
    val onFinish: (GameResult) -> Unit,   // game calls this when over
    val onExit: () -> Unit,               // back/forfeit
)
```
- `PlayScreen(gameId)` looks up the composable from a `GameRegistry(gameId → GamePlay)`, hosts it, and on `onFinish` calls `SubmitScoreUseCase` → repository → Firestore/Room, then shows result + "view leaderboard".
- Each game is just a composable: `fun SnakeGame(scope: GamePlayScope)`. It owns its own loop/state; the shell handles scoring, lifecycle, back-nav, permissions.
- Non-scored games (`ranking == NONE`) skip submission and just show completion.

## Permissions / external deps per game
- mic/camera → `RECORD_AUDIO`, `CAMERA`; CameraX + AudioRecord.
- node_logic → LLM API key via `BuildConfig` (flavor-scoped), called through Retrofit.
- api_pipeline → a public API (e.g., OpenTDB trivia) — pure Retrofit.

## Build order (easy → hard, early wins first)
1. Foundation: Firebase Auth + Firestore leaderboard wired into Hilt/`Result`; `Score`/`ScoreDao`; `GameRegistry` + `PlayScreen` + `GameDetailScreen`; extend `NavRouter`.
2. **Snake** (vertical slice: grid → detail → play → submit → leaderboard) — proves the whole pattern.
3. Flappy, shooting, sketch (all Canvas).
4. Sudoku (generator/solver).
5. api_pipeline (uses your own Retrofit→Room→Repo stack).
6. Chess (engine), Ludo (pass-and-play rules).
7. mic/camera game.
8. node_logic editor (hardest — biggest UI lift + LLM check).

## Open questions (need your call before building)
1. **Sketch & chess & ludo competitiveness** — `ranking == NONE`. Keep them off the leaderboard entirely, or invent a metric (e.g., chess win = +1)? Recommendation: off-leaderboard for v1.
2. **Score trust** — client scores are cheat-able. For a personal/learning app, trust-on-client is fine (note it). Add Cloud-Function validation only if you ever care. OK to defer?
3. **node_logic AI provider** — which LLM/API? (Claude recommended; we'd use the claude-api skill then.) And what *language* of logic (arithmetic gates? boolean? a tiny visual scripting language)?
4. **Registration model** — email/password, or anonymous-first-then-upgrade, or Google Sign-In? Affects the auth screen.

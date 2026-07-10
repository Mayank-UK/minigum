package com.minigum.ui.screens.gamelogic

/**
 * Plain-text documentation for the in-app "Game logic" reference screen.
 *
 * Every entry is derived from the actual game source (engines, bots, ViewModels),
 * so the descriptions match what is really implemented — search depths, RNG sources,
 * numeric constants, AI heuristics and the rendering approach. There is no invented detail.
 */

data class GameSection(
    val heading: String,
    val body: String,
    val bullets: List<String> = emptyList(),
)

data class GameDoc(
    val title: String,
    val emoji: String,
    val tagline: String,
    val sections: List<GameSection>,
)

val gameDocs: List<GameDoc> = listOf(
    GameDoc(
        title = "Snake",
        emoji = "🐍",
        tagline = "Discrete grid snake with a score-driven speed-up.",
        sections = listOf(
            GameSection(
                heading = "Game loop",
                body = "A plain coroutine in the ViewModel (viewModelScope.launch + while(isActive)) sleeps delay(tickDelayMs(score)) then ticks one step. It is not a fixed timestep — the sleep itself shrinks as you score, so the snake gets faster.",
                bullets = listOf(
                    "BASE_DELAY_MS = 200ms, −6ms per food eaten, floored at MIN_DELAY_MS = 70ms.",
                    "Grid is CELLS_PER_SHORT_SIDE = 16 cells on the short side; the long side is scaled to keep cells square.",
                ),
            ),
            GameSection(
                heading = "Mechanics & collision",
                body = "Each tick moves the head by Direction(dx, dy). Self-collision is tested against the body AFTER the tail is removed, so the cell the tail just left is legal to enter. Hitting a wall (out of bounds) or yourself ends the game. Eating = keep the tail (grow +1 score); otherwise drop the tail.",
                bullets = listOf(
                    "180° reversals are rejected via Direction.isOpposite; the turn is queued in pendingDirection and applied next tick so two fast taps can't fold the snake.",
                ),
            ),
            GameSection(
                heading = "Randomness",
                body = "Food is placed uniformly at random over the free cells only: list of all cells minus the snake, then .random(). It can therefore never spawn on top of the snake.",
                bullets = listOf("Source: Kotlin's default Random (List.random), unseeded."),
            ),
            GameSection(
                heading = "Rendering",
                body = "Canvas with rounded-rect cells; head uses colorScheme.primary, body secondary, food a red square. Drag gestures (dominant axis, 30px threshold) steer it; an optional DPad lives in the pause menu.",
            ),
        ),
    ),
    GameDoc(
        title = "Pong",
        emoji = "🏓",
        tagline = "Variable-timestep tennis against a predictive AI.",
        sections = listOf(
            GameSection(
                heading = "Game loop",
                body = "viewModelScope loop, delay(TICK_MS = 16) (~60 Hz), with dt measured from System.nanoTime() and clamped to MAX_DT = 0.033 to stop fast frames from tunneling the ball through a paddle. The field is normalized to height = 1.0 and width = aspect.",
            ),
            GameSection(
                heading = "AI behaviour",
                body = "The opponent only tracks the ball while it is approaching (vx > 0). When the ball moves away it eases back to center at the slower AI_RECENTER_SPEED — that lag is your window to score on quick reversals. It predicts where the ball will reach its edge by integrating t = (targetX − ball.x)/ball.vx and folding the y through a triangle-wave reflection that accounts for wall bounces. It then aims AWAY from you: target = arrival − desiredVySign · (AI_HIT_OFFSET + jitter).",
                bullets = listOf(
                    "AI_SPEED = 0.8 vs AI_RECENTER_SPEED = 0.35; AI_HIT_OFFSET = 0.06; per-rally jitter ±0.02.",
                ),
            ),
            GameSection(
                heading = "Ball physics & scoring",
                body = "Bounce angle depends on where the ball hits the paddle relative to its centre (relative · MAX_BOUNCE_ANGLE, 60°). Each paddle hit speeds the ball up by SPEED_UP = 1.06, capped at MAX_SPEED = 1.35. BASE_SPEED = 0.55. First to WIN_SCORE = 7 wins; a 1.0s serve delay freezes play between points.",
            ),
            GameSection(
                heading = "Randomness",
                body = "kotlin.random.Random drives the serve angle (0.2–0.45 of the field), the serve direction sign, and the per-rally AI jitter.",
            ),
        ),
    ),
    GameDoc(
        title = "Flappy",
        emoji = "🐤",
        tagline = "Gravity + flap; the only difficulty ramp is the shrinking pipe gap.",
        sections = listOf(
            GameSection(
                heading = "Game loop",
                body = "viewModelScope loop, delay(16), dt from nanoTime clamped to 0.033. Normalized field height = 1.0. The scroll speed is constant per run — difficulty rises purely because the gap between pipes shrinks with your score.",
            ),
            GameSection(
                heading = "Mechanics",
                body = "Gravity: vy += GRAVITY·dt (GRAVITY = 2.6), y += vy·dt. A flap is a hard velocity reset to vy = −FLAP_V (0.8), not an additive impulse. Pipes scroll at speed = aspect·0.42; a new pipe spawns once the last has cleared 0.6·aspect, capped at MAX_PIPES = 6 to survive dt spikes.",
                bullets = listOf(
                    "Gap height = (0.3 − score·0.004).coerceAtLeast(0.2) — shrinks 0.004 per point.",
                    "Collision is circle-vs-AABB (nearest-point) against the top and bottom pipe segments.",
                    "Ceiling = bonk (vy = 0); ground (y ≥ 0.9) = death.",
                ),
            ),
            GameSection(
                heading = "Randomness",
                body = "kotlin.random.Random picks the gap centre uniformly within the safe vertical band (between ceiling and ground minus half the gap).",
            ),
        ),
    ),
    GameDoc(
        title = "Chess",
        emoji = "♟️",
        tagline = "Negamax + alpha-beta with a material + piece-square-table evaluation.",
        sections = listOf(
            GameSection(
                heading = "Rules engine",
                body = "ChessEngine is a pure-Kotlin object and the single rules authority. The board is 64 squares (index = rank·8 + file). Legal moves are pseudo-legal moves filtered by 'does it leave my own king attacked'. It implements castling, en-passant and promotion, and detects checkmate, stalemate, the 50-move rule (halfmoveClock ≥ 100) and insufficient material. Threefold repetition is tracked in the ViewModel via Position.key().",
            ),
            GameSection(
                heading = "AI algorithm",
                body = "ChessAi is negamax with alpha-beta pruning. Difficulty is simply search depth: EASY = 2 ply, MEDIUM = 3 ply, HARD = 4 ply. Evaluation is material plus six piece-square tables (centipawns): queen 900, rook 500, bishop 330, knight 320, pawn 100, king ≈ 20,000. Move ordering uses MVV-LVA (captures first: 10,000 + capturedValue − attackerValue) plus promotions. There is no transposition table and no quiescence search.",
                bullets = listOf(
                    "EASY is forgiving: it picks randomly among any move within 100 cp of the best.",
                    "MEDIUM/HARD pick randomly among exact-best moves only.",
                ),
            ),
            GameSection(
                heading = "Randomness",
                body = "The only randomness is the move tie-break (.random()) — there is no search noise.",
            ),
            GameSection(
                heading = "State & polish",
                body = "The AI runs on Dispatchers.Default after a 650 ms think delay, with a staleness guard (monotonic ply) so a late result from a previous position is discarded. Moves slide to their destination over 220 ms (including the castled rook).",
            ),
        ),
    ),
    GameDoc(
        title = "Ludo",
        emoji = "🎲",
        tagline = "1 human vs 3 bots; the dice use unseeded Kotlin Random.",
        sections = listOf(
            GameSection(
                heading = "Dice randomness",
                body = "The real roll is taken once up front — Random.nextInt(1, 7) (a value in 1..6). A cosmetic 9-frame animation (55 ms each) then cycles random faces before committing that original value. Source: kotlin.random.Random, unseeded — so rolls aren't reproducible across launches.",
            ),
            GameSection(
                heading = "Board model",
                body = "A 52-cell main track plus four home columns; four players with four tokens each, start cells at ordinal·13 (Red 0, Green 13, Yellow 26, Blue 39). The engine is coordinate-free, folding track position and home step into a single 0..56 'advance'. A base token leaves only on a 6. Capturing is by landing on a non-safe track cell; SAFE_CELLS = {0, 8, 13, 21, 26, 34, 39, 47}. Win = all four of a player's tokens reach Home(step 5).",
                bullets = listOf(
                    "Three consecutive 6s forfeit the turn; a 6 or a capture grants another roll.",
                ),
            ),
            GameSection(
                heading = "Bot AI",
                body = "LudoBot is a one-ply evaluator: it applies every legal move, scores the resulting state from its own perspective, and picks the maximum (ties broken randomly). It does not look ahead further than that single move.",
                bullets = listOf(
                    "Finish a token +1000 · keep an opponent in its base +120 · avoid expected capture (threats/6)·(token value + 150) · near-home +25 · safe square +20 · progress = advance·5 · base penalty −8.",
                    "threatCount = how many distinct die values (1..6) could let an opponent capture this token next turn.",
                ),
            ),
            GameSection(
                heading = "Rendering",
                body = "A 15×15 Canvas grid with yards, a checkerboard track, safe-cell dots, and four triangles meeting at the centre. Tokens are animated cell-by-cell (90 ms/step); selectable tokens pulse; a 3-up / 2×2 stack layout handles several tokens on one cell.",
            ),
        ),
    ),
    GameDoc(
        title = "Sudoku",
        emoji = "🔢",
        tagline = "A backtracking generator with a uniqueness guarantee.",
        sections = listOf(
            GameSection(
                heading = "Puzzle generation",
                body = "SudokuGenerator first builds a fully solved board by randomized backtracking (shuffling 1..9 at each empty cell). It then removes clues in a shuffled order, keeping a removal ONLY when countSolutions(grid, limit = 2) still equals 1 — so every published puzzle is guaranteed to have a unique solution. Difficulty just sets the target number of remaining clues: EASY = 45, MEDIUM = 38, HARD = 31 (currently hardcoded to MEDIUM).",
            ),
            GameSection(
                heading = "Solver",
                body = "Backtracking with the Minimum-Remaining-Values (MRV) heuristic: always try the empty cell with the fewest legal candidates, failing fast on dead ends. No constraint propagation is used.",
            ),
            GameSection(
                heading = "Input & win detection",
                body = "Notes are a flat bit array indexed cell·9 + (digit − 1). Placing a digit clears that digit from row/col/box peers' notes and counts a mistake if it clashes. The board is complete when it is full with zero conflicts — a valid full grid is necessarily the unique solution, so the app never compares against the stored solution.",
                bullets = listOf("Source: MutableList.shuffle() → Kotlin's default Random; best times are in-memory only, not persisted."),
            ),
        ),
    ),
    GameDoc(
        title = "Logic",
        emoji = "🧩",
        tagline = "A flowchart puzzle judged by a real LLM — there is no in-app interpreter.",
        sections = listOf(
            GameSection(
                heading = "The model",
                body = "You drag blocks (Start, Read, Set, If, While, Output, End) and wire their sockets to build a flowchart. The expression types (Operand, Expr, Condition; BinOp ±×÷; Compare <,≤,=,≥,>) only have render() methods — loops and branches are never executed locally. There is no symbol table and no simulation loop.",
            ),
            GameSection(
                heading = "How it's judged",
                body = "On Check, AiChecker serializes the whole graph to text (blocks + arrows with their socket labels: next/yes/no/body/exit) and POSTs to gemini-2.0-flash via HttpsURLConnection using a bundled API key. A strict tutor prompt asks the model to return exactly CORRECT or INCORRECT plus a <70-word explanation; parseVerdict reads the first line. The five problems are Factorial, Sum 1..N, Maximum of two, Even/odd, and Absolute value.",
                bullets = listOf(
                    "Caveat: the bundled key is extractable from the APK and should be rotated.",
                ),
            ),
            GameSection(
                heading = "World & rendering",
                body = "A 1400×1000 world (BLOCK_W = 150, BLOCK_H = 76) is fit to the viewport with a scale + pan; drag empty space to scroll. Five puzzles cycle with prev/next, and each starts from just a Start and End block.",
            ),
        ),
    ),
    GameDoc(
        title = "Shooter",
        emoji = "👾",
        tagline = "Space-Invaders style; the swarm speeds up as it thins and waves clear.",
        sections = listOf(
            GameSection(
                heading = "Game loop",
                body = "viewModelScope loop, delay(16), dt clamped to 0.033. Normalized field height = 1. A 6×5 grid of aliens marches as one block; at the walls it drops a row and reverses, and if it reaches the player line the game ends.",
            ),
            GameSection(
                heading = "Difficulty ramp & AI",
                body = "March speed = 0.09·aspect·difficulty.marchMult·waveSpeedMul·ramp, where ramp = 1 + (1 − aliveFraction)·1.6 makes the survivors accelerate as the grid thins, and each cleared wave multiplies waveSpeedMul by 1.12. Bombs are dropped stochastically — chance = difficulty.bombRate·dt·(0.5 + 0.5·aliveFraction), only from each column's lowest alien, capped at 5.",
                bullets = listOf(
                    "Difficulty: EASY/NORMAL/HARD = marchMult 0.7/1.0/1.4, bombRate 0.2/0.45/0.85.",
                    "Auto-fire every 0.3s (0.14s with the rapid buff); the spread buff fires 3 bullets at ±0.28 rad.",
                ),
            ),
            GameSection(
                heading = "Scoring & power-ups",
                body = "Collision is AABB throughout (O(bullets × aliens)). Combos give +0.5× every 4 kills up to 4×. Power-ups drop 13% of kills: LIFE 15% / SPREAD 40% / RAPID 45%. You start with 3 lives and get 1.3s of invulnerability after a hit.",
                bullets = listOf("Source: kotlin.random.Random (bomb rolls, drops, firing alien, starfield)."),
            ),
        ),
    ),
    GameDoc(
        title = "Runner",
        emoji = "🏃",
        tagline = "A 3-lane endless runner with a fake-3D projection.",
        sections = listOf(
            GameSection(
                heading = "Game loop",
                body = "viewModelScope loop, delay(16), dt clamped to 0.033. Speed ramps every frame toward MAX_SPEED = 0.95 (START_SPEED = 0.45, RAMP = 0.012); metres += speed·100·dt is your score. The world is three lanes and approaching obstacles — not a procedural terrain heightmap.",
            ),
            GameSection(
                heading = "Movement & obstacles",
                body = "playerLane lerps toward the committed targetLane (LANE_LERP = 11). Jump is grounded-only (no double jump): vy = 0.5 with gravity 1.3; slide lasts 0.55s. Obstacles spawn at spawnInterval = (0.55/speed)·(0.85 + rand·0.3), keeping roughly constant spacing in depth; there's a 25% chance of a second obstacle in another lane, forced to LOW if the first is FULL so a solvable lane always exists.",
                bullets = listOf(
                    "Kind by distance: <120m all LOW (jump); 120–300m 0.6/0.25/0.15 LOW/HIGH/FULL; >300m 0.45/0.30/0.25.",
                ),
            ),
            GameSection(
                heading = "Collision",
                body = "A collision is checked only as an obstacle crosses the player plane (z 0.06 → ≤): LOW hits if you didn't jump high enough (y < 0.04), HIGH hits if you aren't sliding, FULL always hits. 3 lives, 1.3s invulnerability. Fake-3D uses scaleZ = 1/(1 + z·1.6).",
                bullets = listOf("Source: kotlin.random.Random."),
            ),
        ),
    ),
    GameDoc(
        title = "Balloon",
        emoji = "🎈",
        tagline = "Controlled by your voice — microphone loudness lifts the balloon.",
        sections = listOf(
            GameSection(
                heading = "Audio analysis (MicEngine)",
                body = "The only non-touch game. It records mono 16-bit PCM (auto-picking the first working sample rate from 44.1k down to 8k) and on a background thread computes the RMS amplitude — √(Σ sample² / n), normalized to [0,1] — not FFT, not peak. It self-heals: after ≥8 read errors it reopens the recorder, and it re-arms if no sample arrives for 700 ms.",
            ),
            GameSection(
                heading = "Calibration & physics",
                body = "Before playing you calibrate: ~1.6s of silence sets the floor (×1.2), ~1.8s of blowing sets the ceiling. Each tick normalizes raw amplitude to [floor, ceiling] → [0,1] and exponential-smooths it (alpha 0.35). Vertical physics: vy += (−GRAVITY + LIFT·intensity)·dt (GRAVITY 1.2, LIFT 2.9) — louder = rise, silent = fall. Swipes add horizontal impulse + friction.",
            ),
            GameSection(
                heading = "World generation & difficulty",
                body = "Platforms are generated procedurally upward with solid AABB collision (resolved along the smaller-overlap axis); landing on a spring gives vy = +1.8, spikes/birds kill. Difficulty = (height·0.12) raises spike and bird probability and widens platform spacing. A memory cap of MAX_PLATFORMS = 400 drops old platforms. Score = maxHeight·10 metres plus coins.",
                bullets = listOf(
                    "Source: java.util.Random (note — not Kotlin's Random, unlike the other games).",
                    "Requires the RECORD_AUDIO permission; calibration can FAIL on a silent device.",
                ),
            ),
        ),
    ),
)

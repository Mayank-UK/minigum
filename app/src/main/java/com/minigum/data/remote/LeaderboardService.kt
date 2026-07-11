package com.minigum.data.remote

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.minigum.data.remote.dtos.ScoreDto
import com.minigum.data.remote.dtos.ScoreDto.Companion.FIELD_DISPLAY_NAME
import com.minigum.data.remote.dtos.ScoreDto.Companion.FIELD_SCORE
import com.minigum.data.remote.dtos.ScoreDto.Companion.FIELD_UPDATED_AT
import com.minigum.domain.models.LeaderboardGame

/**
 * Thin remote data source over [FirebaseFirestore] for per-game leaderboards.
 *
 * Documents live at `games/{gameId}/scores/{uid}`. The three operations mirror what the repository
 * needs to build the leaderboard UI: the ordered top-N, a count of strictly-better scores (for the
 * current user's rank), and a transactional best-score write.
 */
interface LeaderboardService {

    /** The top [limit] score documents for [game], ordered best-first. */
    suspend fun getTopScores(game: LeaderboardGame, limit: Int): List<ScoreDto>

    /** Number of players with a strictly better score than [score] in [game]. */
    suspend fun countBetterScores(game: LeaderboardGame, score: Int): Long

    /** The current user's stored best-score document, or `null` if they have none. */
    suspend fun getUserScore(game: LeaderboardGame, uid: String): ScoreDto?

    /**
     * Writes [score] for [uid] iff it improves on the stored best, inside a Firestore [Transaction]
     * so concurrent submissions can't clobber a higher score. Returns `true` if the document was
     * created or updated (a new personal record).
     */
    suspend fun submitBestScore(game: LeaderboardGame, uid: String, displayName: String, score: Int): Boolean
}

class LeaderboardServiceImpl(
    private val db: FirebaseFirestore,
) : LeaderboardService {

    private fun scoresCollection(game: LeaderboardGame) = db
        .collection(GAMES_COLLECTION)
        .document(game.id)
        .collection(SCORES_COLLECTION)

    override suspend fun getTopScores(game: LeaderboardGame, limit: Int): List<ScoreDto> {
        val direction = if (game.higherIsBetter) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        val snapshot = scoresCollection(game)
            .orderBy(FIELD_SCORE, direction)
            .limit(limit.toLong())
            .get()
            .awaitValue()
        return snapshot.documents.mapNotNull { ScoreDto.fromDocument(it) }
    }

    override suspend fun countBetterScores(game: LeaderboardGame, score: Int): Long {
        // "Better" depends on the game's sort direction: higher-is-better counts scores above mine;
        // sudoku (lower-is-better) counts scores below mine. Strict inequality so equal scores share
        // a rank rather than pushing each other down.
        val query = if (game.higherIsBetter) {
            scoresCollection(game).whereGreaterThan(FIELD_SCORE, score)
        } else {
            scoresCollection(game).whereLessThan(FIELD_SCORE, score)
        }
        return query.count().get(AggregateSource.SERVER).awaitValue().count
    }

    override suspend fun getUserScore(game: LeaderboardGame, uid: String): ScoreDto? {
        val snapshot = scoresCollection(game).document(uid).get().awaitValue()
        return ScoreDto.fromDocument(snapshot)
    }

    override suspend fun submitBestScore(
        game: LeaderboardGame,
        uid: String,
        displayName: String,
        score: Int,
    ): Boolean {
        val docRef = scoresCollection(game).document(uid)
        return db.runTransaction { txn: Transaction ->
            val snapshot = txn.get(docRef)
            if (!snapshot.exists()) {
                txn.set(docRef, ScoreDto(game.id, uid, displayName.trim(), score).toMap())
                true
            } else {
                val current = (snapshot.get(FIELD_SCORE) as? Long)?.toInt() ?: 0
                val improved = if (game.higherIsBetter) score > current else score < current
                if (improved) {
                    txn.update(
                        docRef,
                        mapOf(
                            FIELD_SCORE to score,
                            FIELD_DISPLAY_NAME to displayName.trim(),
                            FIELD_UPDATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        ),
                    )
                    true
                } else {
                    false
                }
            }
        }.awaitValue()
    }

    companion object {
        private const val GAMES_COLLECTION = "games"
        private const val SCORES_COLLECTION = "scores"
    }
}

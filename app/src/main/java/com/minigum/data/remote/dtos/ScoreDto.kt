package com.minigum.data.remote.dtos

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Wire representation of one user's best score in one game.
 *
 * Stored at `games/{gameId}/scores/{uid}` — a per-game subcollection so the common leaderboard
 * queries (`orderBy("score")`, `whereGreaterThan("score")`) hit Firestore's automatic single-field
 * indexes and need no composite-index management.
 *
 * Field names are centralized here so the document shape has exactly one definition.
 */
data class ScoreDto(
    val gameId: String,
    val uid: String,
    val displayName: String,
    val score: Int,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        FIELD_GAME_ID to gameId,
        FIELD_UID to uid,
        FIELD_DISPLAY_NAME to displayName,
        FIELD_SCORE to score,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    companion object {
        const val FIELD_GAME_ID = "gameId"
        const val FIELD_UID = "uid"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_SCORE = "score"
        const val FIELD_UPDATED_AT = "updatedAt"

        fun fromDocument(doc: DocumentSnapshot): ScoreDto? {
            if (!doc.exists()) return null
            return ScoreDto(
                gameId = doc.getString(FIELD_GAME_ID) ?: return null,
                uid = doc.getString(FIELD_UID) ?: return null,
                displayName = doc.getString(FIELD_DISPLAY_NAME) ?: "Player",
                score = (doc.get(FIELD_SCORE) as? Long)?.toInt() ?: 0,
            )
        }
    }
}

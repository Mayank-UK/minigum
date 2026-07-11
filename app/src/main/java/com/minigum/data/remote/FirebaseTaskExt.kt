package com.minigum.data.remote

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges a Google Play Services [Task] (Firebase Auth / Firestore return these) to a coroutine.
 *
 * Self-contained (no `kotlinx-coroutines-play-services` dependency): cancellable and resumes on the
 * task's success/failure callbacks. Use [awaitValue] for tasks that carry a result and [awaitUnit]
 * for write tasks (e.g. `set`/`update`) that only signal completion.
 */
suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

suspend fun Task<Void>.awaitUnit(): Unit = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(Unit) }
    addOnFailureListener { cont.resumeWithException(it) }
}

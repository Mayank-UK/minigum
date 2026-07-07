package com.minigum.core.result

import com.minigum.core.failures.Failure

/**
 * The outcome of any operation that can fail, surfaced to the domain/UI layers.
 *
 * Mirrors the Result types in the Flutter and iOS guide projects: success carries a value,
 * failure carries a [Failure] (never a throwable). Use [map]/[flatMap]/[recover]/[onSuccess]/
 * [onFailure] to compose and handle results without try/catch at the call site.
 */
sealed class Result<out T> {

    data class Success<out T>(
        val value: T,
        /** Non-blocking failure carried alongside the value, e.g. "showing cached data — network is down". */
        val warning: Failure? = null
    ) : Result<T>()
    data class Failed<out T>(val failure: Failure) : Result<T>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failed

    inline fun <U> map(transform: (T) -> U): Result<U> = when (this) {
        is Success -> Success(transform(value), warning)
        is Failed -> Failed(failure)
    }

    inline fun <U> flatMap(transform: (T) -> Result<U>): Result<U> = when (this) {
        is Success -> when (val next = transform(value)) {
            is Success -> if (next.warning == null) Success(next.value, warning) else next
            is Failed -> next
        }
        is Failed -> Failed(failure)
    }

    inline fun recover(transform: (Failure) -> Result<@UnsafeVariance T>): Result<T> = when (this) {
        is Success -> this
        is Failed -> transform(failure)
    }

    inline fun getOrElse(default: (Failure) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failed -> default(failure)
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (Failure) -> Unit): Result<T> {
        if (this is Failed) action(failure)
        return this
    }
}

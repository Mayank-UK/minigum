package com.minigum.core.guards

import com.minigum.core.exceptions.AppException
import com.minigum.core.exceptions.DatabaseException
import com.minigum.core.exceptions.DecodingException
import com.minigum.core.exceptions.NetworkException
import com.minigum.core.exceptions.ServerException
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Runs a remote/data-source call and translates platform exceptions into [AppException]s,
 * rethrowing them. Already-typed [AppException]s pass through untouched.
 *
 * Call this around every Retrofit/IO call so platform types (HttpException, IOException, Gson)
 * never escape the data layer.
 */
suspend fun <T> guardedApiCall(block: suspend () -> T): T = try {
    block()
} catch (e: AppException) {
    throw e
} catch (e: HttpException) {
    throw mapHttpException(e)
} catch (e: SocketTimeoutException) {
    throw NetworkException.timeout(cause = e)
} catch (e: UnknownHostException) {
    throw NetworkException.noConnection(cause = e)
} catch (e: JsonSyntaxException) {
    throw DecodingException.jsonDecodingFailed(cause = e)
} catch (e: IOException) {
    throw NetworkException("Network error", cause = e)
} catch (e: Exception) {
    throw ServerException("Unexpected API error", cause = e)
}

private fun mapHttpException(e: HttpException): ServerException {
    val code = e.code()
    return when {
        code == 401 -> ServerException.unauthorized(statusCode = code, cause = e)
        code == 404 -> ServerException.notFound(statusCode = code, cause = e)
        code in 400..499 -> ServerException.badRequest(statusCode = code, cause = e)
        code in 500..599 -> ServerException.internalServerError(statusCode = code, cause = e)
        else -> ServerException("HTTP $code", statusCode = code, cause = e)
    }
}

/**
 * Runs a local-database call and translates anything unexpected into a [DatabaseException].
 * Already-typed [AppException]s pass through untouched.
 */
suspend fun <T> guardedDatabaseCall(block: suspend () -> T): T = try {
    block()
} catch (e: AppException) {
    throw e
} catch (e: Exception) {
    throw DatabaseException.readFailed(cause = e)
}

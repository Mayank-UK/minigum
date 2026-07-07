package com.minigum.core.testing

import com.minigum.core.exceptions.DatabaseException
import com.minigum.core.exceptions.DecodingException
import com.minigum.core.exceptions.NetworkException
import com.minigum.core.exceptions.ServerException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Produces pre-built [com.minigum.core.exceptions.AppException]s for tests and
 * manual verification — mirrors the `ErrorTestingHelper` in the Flutter and iOS guide projects.
 */
object ErrorTestingHelper {

    fun simulateNetworkTimeout(): NetworkException =
        NetworkException.timeout(cause = SocketTimeoutException(), context = "Simulated timeout for testing")

    fun simulateNoConnection(): NetworkException =
        NetworkException.noConnection(cause = UnknownHostException(), context = "Simulated no connection for testing")

    fun simulateServerError(): ServerException =
        ServerException.internalServerError(statusCode = 500, context = "Simulated server error for testing")

    fun simulateNotFound(): ServerException =
        ServerException.notFound(statusCode = 404, context = "Simulated not found for testing")

    fun simulateDatabaseError(): DatabaseException =
        DatabaseException.readFailed(context = "Simulated database error for testing")

    fun simulateDecodingError(): DecodingException =
        DecodingException.jsonDecodingFailed(context = "Simulated decoding error for testing")
}

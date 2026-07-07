package com.minigum.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException

object SafeCall {
    suspend fun <T> execute(call: suspend () -> T): Resource<T> {
        return try {
            Resource.Success(call())
        } catch (e: HttpException) {
            Resource.Error(message = "Server error: ${e.code()}", cause = e)
        } catch (e: IOException) {
            Resource.Error(message = "Network error: check your connection", cause = e)
        } catch (e: Exception) {
            Resource.Error(message = "An unexpected error occurred", cause = e)
        }
    }

    fun <T> executeFlow(call: suspend () -> T): Flow<Resource<T>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(call()))
        } catch (e: HttpException) {
            emit(Resource.Error(message = "Server error: ${e.code()}", cause = e))
        } catch (e: IOException) {
            emit(Resource.Error(message = "Network error: check your connection", cause = e))
        } catch (e: Exception) {
            emit(Resource.Error(message = "An unexpected error occurred", cause = e))
        }
    }.flowOn(Dispatchers.IO)
}

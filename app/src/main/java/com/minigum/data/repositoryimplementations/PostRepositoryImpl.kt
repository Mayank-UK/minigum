package com.minigum.data.repositoryimplementations

import com.minigum.core.exceptions.AppException
import com.minigum.core.guards.guardedApiCall
import com.minigum.core.guards.guardedDatabaseCall
import com.minigum.core.guards.safeRepositoryCall
import com.minigum.core.mapping.ExceptionMapper
import com.minigum.core.result.Result
import com.minigum.data.local.daos.PostDao
import com.minigum.data.mappers.PostMapper.toDomain
import com.minigum.data.mappers.PostMapper.toEntity
import com.minigum.data.remote.ApiService
import com.minigum.domain.models.Post
import com.minigum.domain.repositoryinterfaces.PostRepository
import javax.inject.Inject

class PostRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: PostDao
) : PostRepository {

    override suspend fun getPosts(): Result<List<Post>> {
        // On network/server failure, try to recover from the local cache (single source of truth).
        suspend fun fallbackToCache(networkCause: AppException): Result<List<Post>> {
            val networkFailure = ExceptionMapper.map(networkCause)
            return try {
                val cached = guardedDatabaseCall { dao.getPosts() }.map { it.toDomain() }
                when {
                    // Recovered from cache. Carry the network failure as a non-blocking warning so
                    // the UI can show a banner/snackbar alongside the (possibly stale) data.
                    cached.isNotEmpty() -> Result.Success(cached, networkFailure)
                    // Offline and nothing cached — the network failure is the real story for the
                    // user; surface it directly instead of the misleading "no cached data".
                    else -> Result.Failed(networkFailure)
                }
            } catch (dbError: AppException) {
                // Cache read failed on top of the network failure: chain both, with the database
                // failure primary (it is what threw here) and the network failure as its cause.
                Result.Failed(ExceptionMapper.map(dbError).withCause(networkFailure))
            }
        }

        return safeRepositoryCall(
            block = {
                val remotePosts = guardedApiCall { api.getPosts() }
                try {
                    guardedDatabaseCall { dao.insertPosts(remotePosts.map { it.toDomain().toEntity() }) }
                } catch (_: Exception) {
                    // Cache write failure is non-fatal; remote data is still returned.
                }
                remotePosts.map { it.toDomain() }
            },
            onNetworkError = { fallbackToCache(it) },
            onServerError = { fallbackToCache(it) }
        )
    }

    override suspend fun getPostById(id: Int): Result<Post> = safeRepositoryCall(
        block = {
            try {
                guardedApiCall { api.getPostById(id) }.toDomain()
            } catch (apiError: AppException) {
                // Remote failed — fall back to local cache, or rethrow if nothing is cached.
                // Guard the cache read so a DB failure here doesn't clobber the original API error.
                val cached = try {
                    guardedDatabaseCall { dao.getPostById(id) }
                } catch (_: AppException) {
                    null
                }
                cached?.toDomain() ?: throw apiError
            }
        }
    )
}

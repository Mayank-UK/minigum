package com.minigum.data.local.daos

import com.minigum.core.exceptions.DatabaseException
import com.minigum.core.scenario.DbScenario
import com.minigum.data.local.entities.PostEntity

/**
 * Dev-only decorator over the real [PostDao] that simulates DB failure paths. It is intentionally
 * NOT maintained per feature: real SQLite/Room has no chokepoint for clean failure injection (unlike
 * the API, which has the OkHttp [com.minigum.data.remote.FailureInjectionInterceptor]),
 * so this only wraps the Post feature. DB error paths are additionally verified via MockK unit tests.
 *
 * Wired into the dev flavor's DI when `DevConfig.useFakeDatabase` is true; the scenario is supplied
 * by the dev DI module from [com.minigum.core.config.DevConfig] (single source of
 * truth) — no default here so the value can't drift. Staging/prod never use this class.
 */
// Just make it for a single feature to test the DB functionality at least once, do not maintain it for all the features

class FailingPostDao(
    private val inner: PostDao,
    private val scenario: DbScenario,
) : PostDao by inner {

    private fun checkRead() {
        if (scenario == DbScenario.READ_ERROR) {
            throw DatabaseException.readFailed(context = "Simulated DB read error")
        }
    }

    private fun checkWrite() {
        if (scenario == DbScenario.WRITE_ERROR) {
            throw DatabaseException.writeFailed(context = "Simulated DB write error")
        }
    }

    override suspend fun getPosts(): List<PostEntity> {
        checkRead()
        val result = inner.getPosts()
        return if (scenario == DbScenario.EMPTY) emptyList() else result
    }

    override suspend fun getPostById(id: Int): PostEntity? {
        checkRead()
        return inner.getPostById(id)
    }

    override suspend fun getPostsByUserId(userId: Int): List<PostEntity> {
        checkRead()
        return inner.getPostsByUserId(userId)
    }

    override suspend fun insertPosts(posts: List<PostEntity>) {
        checkWrite()
        inner.insertPosts(posts)
    }
}

package com.minigum.core.scenario

/** Failure modes the [com.minigum.data.remote.FakeApiService] can simulate. */
enum class ApiScenario {
    SUCCESS,
    NETWORK_ERROR,
    TIMEOUT,
    SERVER_ERROR,
    INVALID_JSON
}

/** Failure modes the [com.minigum.data.local.daos.FailingPostDao] can simulate. */
enum class DbScenario {
    SUCCESS,
    EMPTY,
    READ_ERROR,
    WRITE_ERROR
}

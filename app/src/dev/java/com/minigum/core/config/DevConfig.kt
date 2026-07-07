package com.minigum.core.config

import com.minigum.core.scenario.ApiScenario
import com.minigum.core.scenario.DbScenario

/**
 * Dev-only simulation knob. **Single source of truth** for the dev flavor.
 *
 * Edit these four values, rebuild the dev flavor, and the app swaps between real
 * and fake API/DB accordingly — no other file needs touching. Mirrors Flutter's
 * `AppConfig` (`apiScenario` / `dbScenario` / `useFakeApi` / `useFakeDatabase`).
 *
 * - [useFakeApi] / [useFakeDatabase]: when true, the dev DI providers return
 *   [com.minigum.data.remote.FakeApiService] /
 *   [com.minigum.data.local.daos.FailingPostDao] instead of the
 *   real Retrofit / Room implementations.
 * - [apiScenario]: drives both the fake API service and the
 *   [com.minigum.data.remote.FailureInjectionInterceptor] (the
 *   latter only matters when [useFakeApi] is false, i.e. against the real API).
 * - [dbScenario]: drives the fake DAO (only matters when [useFakeDatabase] is true).
 *
 * Lives in the dev source set only — staging/prod are always real and never
 * reference this object.
 */
object DevConfig {
    const val useFakeApi: Boolean = false
    const val useFakeDatabase: Boolean = true
    val apiScenario: ApiScenario = ApiScenario.NETWORK_ERROR
    val dbScenario: DbScenario = DbScenario.READ_ERROR
}

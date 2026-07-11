package com.minigum.domain.useCases

import com.minigum.core.result.Result
import com.minigum.domain.repositoryinterfaces.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.signOut()
}

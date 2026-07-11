package com.minigum.domain.useCases

import com.minigum.core.result.Result
import com.minigum.domain.models.UserProfile
import com.minigum.domain.repositoryinterfaces.AuthRepository
import javax.inject.Inject

class SignUpUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, displayName: String): Result<UserProfile> =
        repository.signUp(email, password, displayName)
}

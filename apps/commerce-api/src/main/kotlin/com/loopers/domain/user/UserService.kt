package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

class UserService(
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun getById(id: Long): User =
        userRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")

    fun create(name: String, birth: LocalDate, email: String): User =
        userRepositoryPort.save(User.create(name = name, birth = birth, email = email))
}

package com.loopers.useractivity.infrastructure

import com.loopers.useractivity.domain.UserActionLog
import com.loopers.useractivity.domain.UserActionLogRepository
import org.springframework.stereotype.Repository

@Repository
class UserActionLogRepositoryImpl(
    private val userActionLogJpaRepository: UserActionLogJpaRepository,
) : UserActionLogRepository {
    override fun append(userActionLog: UserActionLog): UserActionLog =
        userActionLogJpaRepository.save(userActionLog)
}

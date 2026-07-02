package com.loopers.infrastructure.useraction.repository

import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.infrastructure.useraction.entity.UserActionLogEntity
import org.springframework.stereotype.Component

@Component
class UserActionLogRepositoryImpl(
    private val userActionLogJpaRepository: UserActionLogJpaRepository,
) : UserActionLogRepository {
    override fun save(userActionLog: UserActionLog): UserActionLog {
        return userActionLogJpaRepository.save(
            UserActionLogEntity(
                eventId = userActionLog.eventId,
                actionType = userActionLog.actionType,
                memberId = userActionLog.memberId,
                aggregateId = userActionLog.aggregateId,
                productId = userActionLog.productId,
                occurredAt = userActionLog.occurredAt,
            ),
        ).toDomain()
    }
}

package com.loopers.application.useraction

import com.loopers.domain.useraction.UserActionLogModel
import com.loopers.infrastructure.useraction.UserActionLogRepository
import com.loopers.interfaces.consumer.message.UserActionLoggedMessage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserActionLogService(
    private val userActionLogRepository: UserActionLogRepository,
) {
    @Transactional
    fun log(message: UserActionLoggedMessage) {
        userActionLogRepository.save(
            UserActionLogModel(
                userId = message.userId,
                actionType = message.actionType,
                targetId = message.targetId,
                occurredAt = message.occurredAt,
            ),
        )
    }
}

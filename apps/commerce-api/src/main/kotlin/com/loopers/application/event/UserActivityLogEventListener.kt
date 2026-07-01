package com.loopers.application.event

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActivityLogEventListener {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: UserActivityEvent) {
        log.info(
            "유저 행동: userId={}, type={}, {}",
            event.userId,
            event.activityType,
            event.description,
        )
    }
}

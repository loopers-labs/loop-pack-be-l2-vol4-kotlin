package com.loopers.failure.application

import com.loopers.failure.infrastructure.ConsumedEventFailure
import com.loopers.failure.infrastructure.ConsumedEventFailureJpaRepository
import com.loopers.notification.NotificationSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConsumedEventFailureRecorder(
    private val consumedEventFailureJpaRepository: ConsumedEventFailureJpaRepository,
    private val notificationSender: NotificationSender,
) {
    @Transactional
    fun record(failure: ConsumedEventFailure) {
        consumedEventFailureJpaRepository.save(failure)
        notificationSender.notify(
            "소비 실패 이벤트 DLT 격리",
            "topic=${failure.originalTopic} offset=${failure.originalOffset} " +
                "group=${failure.consumerGroup} exception=${failure.exceptionFqcn}: ${failure.exceptionMessage}",
        )
    }
}

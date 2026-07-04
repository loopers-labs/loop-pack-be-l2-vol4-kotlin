package com.loopers.interfaces.consumer

import com.loopers.application.useraction.UserActionLogService
import com.loopers.config.kafka.KafkaTopics.USER_ACTION_EVENTS
import com.loopers.configuration.DeadLetterConfig
import com.loopers.interfaces.consumer.message.UserActionLoggedMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class UserActionEventConsumer(
    private val userActionLogService: UserActionLogService,
) {
    @KafkaListener(
        topics = [USER_ACTION_EVENTS],
        groupId = "user-action-log",
        containerFactory = DeadLetterConfig.SINGLE_LISTENER,
    )
    fun consume(message: UserActionLoggedMessage, ack: Acknowledgment) {
        userActionLogService.log(message)
        ack.acknowledge()
    }
}

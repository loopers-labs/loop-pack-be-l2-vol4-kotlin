package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import com.loopers.projection.like.application.LikeCountProjectionCommand
import com.loopers.projection.like.application.LikeCountProjectionService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class LikeCountEventConsumer(
    private val likeCountProjectionService: LikeCountProjectionService,
) {
    @KafkaListener(
        topics = ["\${commerce-events.like-count.topic-name}"],
        groupId = CONSUMER_GROUP,
        containerFactory = KafkaConfig.RECORD_LISTENER,
    )
    fun consume(
        event: LikeCountChangedEvent,
        acknowledgment: Acknowledgment,
    ) {
        require(event.eventType == EVENT_TYPE) {
            "지원하지 않는 좋아요 수 이벤트 타입입니다: ${event.eventType}"
        }

        likeCountProjectionService.project(
            LikeCountProjectionCommand(
                eventId = event.eventId,
                consumerGroup = CONSUMER_GROUP,
                eventType = event.eventType,
                productId = event.productId,
                delta = event.delta,
            ),
        )
        acknowledgment.acknowledge()
    }

    companion object {
        const val CONSUMER_GROUP = "commerce-streamer-like-count"
        const val EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
    }
}

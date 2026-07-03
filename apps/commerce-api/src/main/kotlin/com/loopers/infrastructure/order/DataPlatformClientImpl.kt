package com.loopers.infrastructure.order

import com.loopers.domain.order.DataPlatformClient
import com.loopers.domain.order.OrderCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 데이터 플랫폼 전송 클라이언트.
 * 실제 외부 시스템 연동 전까지는 로깅으로 대체하며, Round 7 Step 2 에서 Kafka 발행으로 대체된다.
 */
@Component
class DataPlatformClientImpl : DataPlatformClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(event: OrderCreatedEvent) {
        log.info(
            "데이터 플랫폼 주문 전송: orderId={}, userId={}, finalAmount={}, items={}",
            event.orderId,
            event.userId,
            event.finalAmount,
            event.items,
        )
    }
}

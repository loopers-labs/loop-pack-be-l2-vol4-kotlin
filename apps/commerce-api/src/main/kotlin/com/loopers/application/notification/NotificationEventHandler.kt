package com.loopers.application.notification

import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 알림 스텁: 실제 전송 없이 "발송했다"는 사실만 로깅한다.
// 주 로직(주문/결제)과 부가 로직(알림)의 경계를 ApplicationEvent 로 분리한 예시.
@Component
class NotificationEventHandler {
    private val log = LoggerFactory.getLogger(NotificationEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderCreatedEvent) = log.info(message(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentSucceededEvent) = log.info(message(event))

    fun message(event: OrderCreatedEvent) = "NOTIFY user=${event.userId} 주문(${event.orderId})이 접수되었습니다."
    fun message(event: PaymentSucceededEvent) = "NOTIFY user=${event.userId} 주문(${event.orderId}) 결제가 완료되었습니다."
}

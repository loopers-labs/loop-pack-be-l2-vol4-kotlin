package com.loopers.application.log

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.product.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionLogEventHandler {
    private val log = LoggerFactory.getLogger(UserActionLogEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductViewedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeCreatedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeDeletedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderCreatedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentSucceededEvent) = log.info(describe(event))

    fun describe(event: ProductViewedEvent) = "USER_ACTION type=VIEW productId=${event.productId}"
    fun describe(event: LikeCreatedEvent) = "USER_ACTION type=LIKE productId=${event.productId}"
    fun describe(event: LikeDeletedEvent) = "USER_ACTION type=UNLIKE productId=${event.productId}"
    fun describe(event: OrderCreatedEvent) =
        "USER_ACTION type=ORDER userId=${event.userId} orderId=${event.orderId} items=${event.items.size}"
    fun describe(event: PaymentSucceededEvent) =
        "USER_ACTION type=PAYMENT userId=${event.userId} orderId=${event.orderId} items=${event.items.size}"
}

package com.loopers.domain.useraction

import java.time.ZonedDateTime

class UserActionLog(
    val eventId: String,
    val actionType: UserActionType,
    val memberId: Long?,
    val aggregateId: Long,
    val productId: Long?,
    val occurredAt: ZonedDateTime,
)

enum class UserActionType {
    PRODUCT_LIKED,
    PRODUCT_UNLIKED,
    PRODUCT_VIEWED,
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
}

package com.loopers.domain.coupon

interface CouponPublishInboxRepository {
    fun saveAndFlush(inbox: CouponPublishInbox): CouponPublishInbox

    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
}

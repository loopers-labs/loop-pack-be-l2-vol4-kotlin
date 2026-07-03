package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel

data class PublishCall(
    val event: OutboxEventModel,
    val transactionActive: Boolean,
)

package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import org.springframework.transaction.support.TransactionSynchronizationManager

class RecordingOutboxEventPublisher : OutboxEventPublisher {
    val calls = mutableListOf<PublishCall>()
    var failWith: RuntimeException? = null

    override fun publish(event: OutboxEventModel) {
        calls.add(
            PublishCall(
                event = event,
                transactionActive = TransactionSynchronizationManager.isActualTransactionActive(),
            ),
        )
        failWith?.let { throw it }
    }

    fun reset() {
        calls.clear()
        failWith = null
    }
}

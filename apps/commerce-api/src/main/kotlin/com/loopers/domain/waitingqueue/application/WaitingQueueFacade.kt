package com.loopers.domain.waitingqueue.application

import com.loopers.domain.waitingqueue.application.service.WaitingQueueService
import com.loopers.domain.waitingqueue.model.AdmissionBatchResult
import com.loopers.domain.waitingqueue.model.WaitingQueueState
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import org.springframework.stereotype.Component

@Component
class WaitingQueueFacade(
    private val waitingQueueService: WaitingQueueService,
) {
    fun enter(userId: Long): WaitingQueueState = waitingQueueService.enter(userId)

    fun position(userId: Long): WaitingQueueState = waitingQueueService.position(userId)

    fun admitBatch(): AdmissionBatchResult = waitingQueueService.admitBatch()

    fun validateForOrder(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ): TokenValidationResult = waitingQueueService.validateForOrder(userId, token, idempotencyKey)

    fun consumeAfterOrderCreated(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ) {
        waitingQueueService.consumeAfterOrderCreated(userId, token, idempotencyKey)
    }

    fun releaseAfterOrderFailed(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ) {
        waitingQueueService.releaseAfterOrderFailed(userId, token, idempotencyKey)
    }
}

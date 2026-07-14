package com.loopers.domain.order.application

import com.loopers.domain.order.application.command.OrderCreateCommand
import com.loopers.domain.order.application.info.OrderInfo
import com.loopers.domain.order.constant.OrderErrorMessages
import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderQueueGateFacade(
    private val waitingQueueFacade: WaitingQueueFacade,
    private val orderFacade: OrderFacade,
    private val gatePolicy: OrderQueueGatePolicy,
) {
    fun placeOrder(
        command: OrderCreateCommand,
        queueToken: String?,
        queueIdempotencyKey: String?,
    ): OrderInfo {
        if (!gatePolicy.requiresAdmission(command)) {
            return orderFacade.placeOrder(command)
        }
        val requiredQueueToken = queueToken
            ?.takeIf { it.isNotBlank() }
            ?: throw CoreException(ErrorType.UNAUTHORIZED)
        val requiredIdempotencyKey = queueIdempotencyKey
            ?.takeIf { it.isNotBlank() }
            ?: throw CoreException(ErrorType.BAD_REQUEST)
        val validation = try {
            waitingQueueFacade.validateForOrder(
                command.userId,
                requiredQueueToken,
                requiredIdempotencyKey,
            )
        } catch (tokenRejection: CoreException) {
            if (tokenRejection.errorType != ErrorType.UNAUTHORIZED) {
                throw tokenRejection
            }
            val committedOrder = try {
                findCommittedOrder(command.userId, requiredIdempotencyKey)
            } catch (recoveryFailure: RuntimeException) {
                throwRecoveryUnavailable(tokenRejection, recoveryFailure)
            }
            committedOrder?.let { return it }
            throw tokenRejection
        }
        when (validation.status) {
            TokenValidationResult.Status.PROCESSING_BY_SAME_IDEMPOTENCY_KEY ->
                return recoverProcessingOrder(
                    userId = command.userId,
                    queueToken = requiredQueueToken,
                    idempotencyKey = requiredIdempotencyKey,
                )
            TokenValidationResult.Status.CONSUMED_BY_SAME_IDEMPOTENCY_KEY ->
                return findCommittedOrder(command.userId, requiredIdempotencyKey)
                    ?: throw CoreException(
                        ErrorType.SERVICE_UNAVAILABLE,
                        OrderErrorMessages.IDEMPOTENT_ORDER_NOT_FOUND,
                    )
            TokenValidationResult.Status.VALID -> Unit
            else -> throw CoreException(ErrorType.UNAUTHORIZED)
        }
        val order = try {
            orderFacade.placeOrder(command)
        } catch (e: RuntimeException) {
            recoverTokenAfterOrderFailure(
                userId = command.userId,
                queueToken = requiredQueueToken,
                idempotencyKey = requiredIdempotencyKey,
                originalFailure = e,
            )
            throw e
        }
        waitingQueueFacade.consumeAfterOrderCreated(command.userId, requiredQueueToken, requiredIdempotencyKey)
        return order
    }

    private fun findCommittedOrder(userId: Long, idempotencyKey: String): OrderInfo? =
        orderFacade.findByIdempotencyKeyOrNull(userId, idempotencyKey)

    private fun recoverProcessingOrder(
        userId: Long,
        queueToken: String,
        idempotencyKey: String,
    ): OrderInfo {
        val committedOrder = findCommittedOrder(userId, idempotencyKey)
            ?: throw CoreException(
                ErrorType.CONFLICT,
                OrderErrorMessages.SAME_IDEMPOTENCY_ORDER_IN_PROGRESS,
            )
        waitingQueueFacade.consumeAfterOrderCreated(userId, queueToken, idempotencyKey)
        return committedOrder
    }

    private fun recoverTokenAfterOrderFailure(
        userId: Long,
        queueToken: String,
        idempotencyKey: String,
        originalFailure: RuntimeException,
    ) {
        val committedOrder = try {
            findCommittedOrder(userId, idempotencyKey)
        } catch (recoveryFailure: RuntimeException) {
            throwRecoveryUnavailable(originalFailure, recoveryFailure)
        }
        try {
            if (committedOrder == null) {
                waitingQueueFacade.releaseAfterOrderFailed(userId, queueToken, idempotencyKey)
            } else {
                waitingQueueFacade.consumeAfterOrderCreated(userId, queueToken, idempotencyKey)
            }
        } catch (tokenTransitionFailure: RuntimeException) {
            throwRecoveryUnavailable(originalFailure, tokenTransitionFailure)
        }
    }

    private fun throwRecoveryUnavailable(
        originalFailure: RuntimeException,
        recoveryFailure: RuntimeException,
    ): Nothing {
        val unavailable = (recoveryFailure as? CoreException)
            ?.takeIf { it.errorType == ErrorType.SERVICE_UNAVAILABLE }
            ?: CoreException(ErrorType.SERVICE_UNAVAILABLE, cause = recoveryFailure)
        if (unavailable !== originalFailure) {
            unavailable.addSuppressed(originalFailure)
        }
        throw unavailable
    }
}

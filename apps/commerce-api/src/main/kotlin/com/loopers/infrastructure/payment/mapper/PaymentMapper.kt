package com.loopers.infrastructure.payment.mapper

import com.loopers.domain.payment.Payment
import com.loopers.infrastructure.payment.entity.PaymentEntity

object PaymentMapper {
    fun toDomain(entity: PaymentEntity): Payment {
        return Payment(
            id = entity.id,
            orderId = entity.orderId,
            orderNumber = entity.orderNumber,
            memberId = entity.memberId,
            idempotencyKey = entity.idempotencyKey,
            amount = entity.amount,
            cardType = entity.cardType,
            cardNo = entity.cardNo,
            status = entity.status,
            transactionKey = entity.transactionKey,
            reason = entity.reason,
        )
    }

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            orderId = payment.orderId,
            orderNumber = payment.orderNumber,
            memberId = payment.memberId,
            idempotencyKey = payment.idempotencyKey,
            amount = payment.amount,
            cardType = payment.cardType,
            cardNo = payment.cardNo,
            status = payment.status,
            transactionKey = payment.transactionKey,
            reason = payment.reason,
        )
    }
}

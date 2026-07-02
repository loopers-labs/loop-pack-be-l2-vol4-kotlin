package com.loopers.domain.payment.event

import com.loopers.domain.payment.Payment
import java.time.ZonedDateTime
import java.util.UUID

object PaymentEvent {
    data class Requested(
        val paymentId: Long,
        val orderId: Long,
        val orderNumber: String,
        val memberId: Long,
        val amount: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
    ) {
        companion object {
            fun from(payment: Payment): Requested {
                return Requested(
                    paymentId = payment.id,
                    orderId = payment.orderId,
                    orderNumber = payment.orderNumber,
                    memberId = payment.memberId,
                    amount = payment.amount,
                )
            }
        }
    }

    data class Succeeded(
        val paymentId: Long,
        val orderId: Long,
        val orderNumber: String,
        val memberId: Long,
        val amount: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
    ) {
        companion object {
            fun from(payment: Payment): Succeeded {
                return Succeeded(
                    paymentId = payment.id,
                    orderId = payment.orderId,
                    orderNumber = payment.orderNumber,
                    memberId = payment.memberId,
                    amount = payment.amount,
                )
            }
        }
    }

    data class Failed(
        val paymentId: Long,
        val orderId: Long,
        val orderNumber: String,
        val memberId: Long,
        val amount: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
    ) {
        companion object {
            fun from(payment: Payment): Failed {
                return Failed(
                    paymentId = payment.id,
                    orderId = payment.orderId,
                    orderNumber = payment.orderNumber,
                    memberId = payment.memberId,
                    amount = payment.amount,
                )
            }
        }
    }
}

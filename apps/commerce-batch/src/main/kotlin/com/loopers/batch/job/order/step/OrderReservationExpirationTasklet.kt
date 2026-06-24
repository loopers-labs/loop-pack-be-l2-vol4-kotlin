package com.loopers.batch.job.order.step

import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.LocalDateTime

@Component
class OrderReservationExpirationTasklet(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val now = LocalDateTime.now()
        val orderIds = jdbcTemplate.queryForList(
            """
            select id
              from orders
             where status = 'PAYMENT_PENDING'
               and reservation_expires_at <= :now
               and deleted_at is null
            """.trimIndent(),
            mapOf("now" to Timestamp.valueOf(now)),
            Long::class.java,
        )
        orderIds.forEach { orderId ->
            transactionTemplate.executeWithoutResult {
                expireOrder(orderId)
            }
        }
        return RepeatStatus.FINISHED
    }

    private fun expireOrder(orderId: Long) {
        // Expiration deliberately targets only PAYMENT_PENDING orders; FAILED orders may already have approved PG payment.
        val reservations = jdbcTemplate.queryForList(
            """
            select id, product_id, quantity
              from stock_reservations
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        if (reservations.isEmpty()) return

        val updatedReservations = jdbcTemplate.update(
            """
            update stock_reservations
               set status = 'EXPIRED', updated_at = now()
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        require(updatedReservations == reservations.size) { "reservation affected row mismatch orderId=$orderId" }

        reservations.groupBy { (it["product_id"] as Number).toLong() }
            .mapValues { entry -> entry.value.sumOf { (it["quantity"] as Number).toInt() } }
            .forEach { (productId, quantity) ->
                val updatedStock = jdbcTemplate.update(
                    """
                    update product_stocks
                       set reserved_quantity = reserved_quantity - :quantity,
                           updated_at = now()
                     where product_id = :productId
                       and reserved_quantity >= :quantity
                       and deleted_at is null
                    """.trimIndent(),
                    mapOf("productId" to productId, "quantity" to quantity),
                )
                require(updatedStock == 1) { "stock release affected row mismatch orderId=$orderId productId=$productId" }
            }

        val updatedOrder = jdbcTemplate.update(
            """
            update orders
               set status = 'EXPIRED', updated_at = now()
             where id = :orderId
               and status = 'PAYMENT_PENDING'
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        require(updatedOrder == 1) { "order expire affected row mismatch orderId=$orderId" }

        jdbcTemplate.update(
            """
            update payments
               set status = 'EXPIRED', updated_at = now()
             where order_id = :orderId
               and status in ('READY', 'VERIFY_FAILED')
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        appendPaymentEvent(orderId, "EXPIRED", "reservation expired")
    }

    private fun appendPaymentEvent(orderId: Long, eventType: String, rawResponseSummary: String) {
        val payment = jdbcTemplate.queryForMap(
            "select * from payments where order_id = :orderId and deleted_at is null",
            mapOf("orderId" to orderId),
        )
        jdbcTemplate.update(
            """
            insert into payment_events (
                order_id, payment_id, event_type, pg_provider, payment_request_id, payment_key, pg_transaction_id,
                requested_amount, approved_amount, pg_status, failure_reason, raw_response_summary, created_at, updated_at
            ) values (
                :orderId, :paymentId, :eventType, :pgProvider, :paymentRequestId, :paymentKey, :pgTransactionId,
                :requestedAmount, :approvedAmount, :pgStatus, :failureReason, :rawResponseSummary, now(), now()
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("paymentId", payment["id"])
                .addValue("eventType", eventType)
                .addValue("pgProvider", payment["pg_provider"])
                .addValue("paymentRequestId", payment["payment_request_id"])
                .addValue("paymentKey", payment["payment_key"])
                .addValue("pgTransactionId", payment["pg_transaction_id"])
                .addValue("requestedAmount", payment["requested_amount"])
                .addValue("approvedAmount", payment["approved_amount"])
                .addValue("pgStatus", null)
                .addValue("failureReason", null)
                .addValue("rawResponseSummary", rawResponseSummary),
        )
    }
}

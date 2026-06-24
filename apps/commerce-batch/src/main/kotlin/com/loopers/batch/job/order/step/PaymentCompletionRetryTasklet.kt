package com.loopers.batch.job.order.step

import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class PaymentCompletionRetryTasklet(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) : Tasklet {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val rows = jdbcTemplate.queryForList(
            """
            select p.order_id
              from payments p
              join orders o on o.id = p.order_id
             where o.status = 'FAILED'
               and p.status = 'COMPLETION_FAILED'
               and p.completion_retry_count < 3
               and p.deleted_at is null
               and o.deleted_at is null
             order by p.updated_at asc
             limit 100
            """.trimIndent(),
            emptyMap<String, Any>(),
        )
        rows.map { (it["order_id"] as Number).toLong() }
            .forEach(::retryCompletion)
        return RepeatStatus.FINISHED
    }

    private fun retryCompletion(orderId: Long) {
        val payment = payment(orderId)
        val pgTransactionId = payment["pg_transaction_id"] as String? ?: "payment-$orderId"
        val requestedAmount = (payment["requested_amount"] as Number).toLong()
        appendPaymentEvent(orderId, "VERIFY_REQUESTED", null, "verify requested")

        // Batch cannot depend on commerce-api services, so it mirrors the same projection/event transitions in SQL.
        runCatching {
            transactionTemplate.executeWithoutResult {
                completeOrder(orderId, pgTransactionId, requestedAmount)
                appendPaymentEvent(orderId, "VERIFY_SUCCEEDED", "APPROVED", "verify succeeded")
            }
        }.getOrElse { throwable ->
            transactionTemplate.executeWithoutResult {
                incrementCompletionRetryFailure(orderId, throwable)
            }
        }
    }

    private fun completeOrder(orderId: Long, pgTransactionId: String, approvedAmount: Long) {
        val reservations = inProgressReservations(orderId)
        require(reservations.isNotEmpty()) { "in-progress reservation not found orderId=$orderId" }

        reservations.groupBy { (it["product_id"] as Number).toLong() }
            .mapValues { entry -> entry.value.sumOf { (it["quantity"] as Number).toInt() } }
            .forEach { (productId, quantity) ->
                val updatedStock = jdbcTemplate.update(
                    """
                    update product_stocks
                       set stock_quantity = stock_quantity - :quantity,
                           reserved_quantity = reserved_quantity - :quantity,
                           updated_at = now()
                     where product_id = :productId
                       and stock_quantity >= :quantity
                       and reserved_quantity >= :quantity
                       and deleted_at is null
                    """.trimIndent(),
                    mapOf("productId" to productId, "quantity" to quantity),
                )
                require(updatedStock == 1) { "stock confirm affected row mismatch orderId=$orderId productId=$productId" }
            }

        val updatedReservations = jdbcTemplate.update(
            """
            update stock_reservations
               set status = 'COMPLETED', updated_at = now()
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        require(updatedReservations == reservations.size) { "reservation affected row mismatch orderId=$orderId" }

        val updatedOrder = jdbcTemplate.update(
            """
            update orders
               set status = 'COMPLETED', updated_at = now()
             where id = :orderId
               and status = 'FAILED'
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        require(updatedOrder == 1) { "order complete affected row mismatch orderId=$orderId" }

        val updatedPayment = jdbcTemplate.update(
            """
            update payments
               set status = 'APPROVED',
                   pg_transaction_id = :pgTransactionId,
                   approved_amount = :approvedAmount,
                   approved_at = coalesce(approved_at, now()),
                   failure_reason = null,
                   updated_at = now()
             where order_id = :orderId
               and status = 'COMPLETION_FAILED'
            """.trimIndent(),
            mapOf(
                "orderId" to orderId,
                "pgTransactionId" to pgTransactionId,
                "approvedAmount" to approvedAmount,
            ),
        )
        require(updatedPayment == 1) { "payment approve affected row mismatch orderId=$orderId" }
    }

    private fun incrementCompletionRetryFailure(orderId: Long, throwable: Throwable) {
        val reason = (throwable.message ?: throwable.javaClass.simpleName).take(500)
        val updatedPayment = jdbcTemplate.update(
            """
            update payments
               set completion_retry_count = completion_retry_count + 1,
                   last_failed_at = now(),
                   failure_reason = :reason,
                   updated_at = now()
             where order_id = :orderId
               and status = 'COMPLETION_FAILED'
            """.trimIndent(),
            mapOf("orderId" to orderId, "reason" to reason),
        )
        require(updatedPayment == 1) { "payment retry failure affected row mismatch orderId=$orderId" }
        appendPaymentEvent(orderId, "COMPLETION_FAILED", null, "internal completion retry failed")

        val payment = payment(orderId)
        val retryCount = (payment["completion_retry_count"] as Number).toInt()
        if (retryCount >= 3) {
            logRetryStopped(orderId, payment, reason, retryCount, throwable)
        }
    }

    private fun logRetryStopped(
        orderId: Long,
        payment: Map<String, Any>,
        reason: String,
        retryCount: Int,
        throwable: Throwable,
    ) {
        val reservations = inProgressReservations(orderId)
        val productQuantities = reservations
            .groupBy { (it["product_id"] as Number).toLong() }
            .mapValues { entry -> entry.value.sumOf { (it["quantity"] as Number).toInt() } }
        logger.error(
            "payment completion retry stopped orderId={} paymentId={} pgProvider={} pgTransactionId={} " +
                "reservationIds={} productQuantities={} reason={} retryCount={}",
            orderId,
            payment["id"],
            payment["pg_provider"],
            payment["pg_transaction_id"],
            reservations.map { it["id"] },
            productQuantities,
            reason,
            retryCount,
            throwable,
        )
    }

    private fun payment(orderId: Long): Map<String, Any> =
        jdbcTemplate.queryForMap(
            "select * from payments where order_id = :orderId and deleted_at is null",
            mapOf("orderId" to orderId),
        )

    private fun inProgressReservations(orderId: Long): List<Map<String, Any>> =
        jdbcTemplate.queryForList(
            """
            select id, product_id, quantity
              from stock_reservations
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )

    private fun appendPaymentEvent(
        orderId: Long,
        eventType: String,
        pgStatus: String?,
        rawResponseSummary: String,
    ) {
        val payment = payment(orderId)
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
                .addValue("pgStatus", pgStatus)
                .addValue("failureReason", payment["failure_reason"])
                .addValue("rawResponseSummary", rawResponseSummary),
        )
    }
}

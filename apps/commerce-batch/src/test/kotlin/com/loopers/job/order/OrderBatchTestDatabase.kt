package com.loopers.job.order

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Timestamp
import java.time.LocalDateTime

internal class OrderBatchTestDatabase(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun resetSchema() {
        jdbcTemplate.jdbcOperations.execute("drop table if exists payment_events")
        jdbcTemplate.jdbcOperations.execute("drop table if exists payments")
        jdbcTemplate.jdbcOperations.execute("drop table if exists stock_reservations")
        jdbcTemplate.jdbcOperations.execute("drop table if exists product_stocks")
        jdbcTemplate.jdbcOperations.execute("drop table if exists orders")
        createTables()
    }

    fun insertOrder(
        orderId: Long,
        status: String,
        reservationExpiresAt: LocalDateTime,
    ) {
        jdbcTemplate.update(
            """
            insert into orders (
                id, user_id, reservation_expires_at, delivery_address, delivery_request,
                phone_number, coupon_id, total_amount, discount_amount, payment_amount,
                status, cancel_reason, created_at, updated_at, deleted_at
            ) values (
                :orderId, 1, :reservationExpiresAt, 'Seoul', 'Front door',
                '010-1234-5678', null, 2000, 0, 2000,
                :status, null, now(), now(), null
            )
            """.trimIndent(),
            mapOf(
                "orderId" to orderId,
                "reservationExpiresAt" to Timestamp.valueOf(reservationExpiresAt),
                "status" to status,
            ),
        )
    }

    fun insertProductStock(productId: Long, stockQuantity: Int, reservedQuantity: Int) {
        jdbcTemplate.update(
            """
            insert into product_stocks (
                id, product_id, stock_quantity, reserved_quantity, created_at, updated_at, deleted_at
            ) values (
                :productId, :productId, :stockQuantity, :reservedQuantity, now(), now(), null
            )
            """.trimIndent(),
            mapOf(
                "productId" to productId,
                "stockQuantity" to stockQuantity,
                "reservedQuantity" to reservedQuantity,
            ),
        )
    }

    fun insertReservation(
        reservationId: Long,
        orderId: Long,
        productId: Long,
        quantity: Int,
        status: String,
    ) {
        jdbcTemplate.update(
            """
            insert into stock_reservations (
                id, order_id, product_id, quantity, status, created_at, updated_at, deleted_at
            ) values (
                :reservationId, :orderId, :productId, :quantity, :status, now(), now(), null
            )
            """.trimIndent(),
            mapOf(
                "reservationId" to reservationId,
                "orderId" to orderId,
                "productId" to productId,
                "quantity" to quantity,
                "status" to status,
            ),
        )
    }

    fun insertPayment(
        paymentId: Long,
        orderId: Long,
        status: String,
        requestedAmount: Long,
        paymentKey: String? = null,
        pgTransactionId: String? = null,
        approvedAmount: Long? = null,
        completionRetryCount: Int = 0,
        failureReason: String? = null,
    ) {
        jdbcTemplate.update(
            """
            insert into payments (
                id, order_id, status, pg_provider, payment_request_id, requested_amount,
                payment_key, pg_transaction_id, approved_amount, failure_reason,
                completion_retry_count, approved_at, canceled_at, last_failed_at,
                created_at, updated_at, deleted_at
            ) values (
                :paymentId, :orderId, :status, 'FAKE', :paymentRequestId, :requestedAmount,
                :paymentKey, :pgTransactionId, :approvedAmount, :failureReason,
                :completionRetryCount, null, null, null, now(), now(), null
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("paymentId", paymentId)
                .addValue("orderId", orderId)
                .addValue("status", status)
                .addValue("paymentRequestId", "order-$orderId-request")
                .addValue("requestedAmount", requestedAmount)
                .addValue("paymentKey", paymentKey)
                .addValue("pgTransactionId", pgTransactionId)
                .addValue("approvedAmount", approvedAmount)
                .addValue("failureReason", failureReason)
                .addValue("completionRetryCount", completionRetryCount),
        )
    }

    fun orderStatus(orderId: Long): String =
        queryString("select status from orders where id = :orderId", "orderId" to orderId)

    fun paymentStatus(orderId: Long): String =
        queryString("select status from payments where order_id = :orderId", "orderId" to orderId)

    fun reservationStatus(orderId: Long): String =
        queryString("select status from stock_reservations where order_id = :orderId", "orderId" to orderId)

    fun stockQuantity(productId: Long): Int =
        queryInt("select stock_quantity from product_stocks where product_id = :productId", "productId" to productId)

    fun reservedQuantity(productId: Long): Int =
        queryInt("select reserved_quantity from product_stocks where product_id = :productId", "productId" to productId)

    private fun queryString(sql: String, parameter: Pair<String, Long>): String =
        jdbcTemplate.queryForObject(sql, mapOf(parameter), String::class.java)!!

    private fun queryInt(sql: String, parameter: Pair<String, Long>): Int =
        jdbcTemplate.queryForObject(sql, mapOf(parameter), Int::class.java)!!

    private fun createTables() {
        jdbcTemplate.jdbcOperations.execute(
            """
            create table orders (
                id bigint not null primary key,
                user_id bigint not null,
                reservation_expires_at datetime(6) not null,
                delivery_address varchar(500) not null,
                delivery_request varchar(500) not null,
                phone_number varchar(30) not null,
                coupon_id bigint,
                total_amount bigint not null,
                discount_amount bigint not null,
                payment_amount bigint not null,
                status varchar(30) not null,
                cancel_reason varchar(30),
                created_at datetime(6) not null,
                updated_at datetime(6) not null,
                deleted_at datetime(6)
            )
            """.trimIndent(),
        )
        jdbcTemplate.jdbcOperations.execute(
            """
            create table product_stocks (
                id bigint not null primary key,
                product_id bigint not null,
                stock_quantity int not null,
                reserved_quantity int not null,
                created_at datetime(6) not null,
                updated_at datetime(6) not null,
                deleted_at datetime(6),
                unique key uk_product_stocks_product_id (product_id)
            )
            """.trimIndent(),
        )
        jdbcTemplate.jdbcOperations.execute(
            """
            create table stock_reservations (
                id bigint not null primary key,
                order_id bigint not null,
                product_id bigint not null,
                quantity int not null,
                status varchar(30) not null,
                created_at datetime(6) not null,
                updated_at datetime(6) not null,
                deleted_at datetime(6)
            )
            """.trimIndent(),
        )
        jdbcTemplate.jdbcOperations.execute(
            """
            create table payments (
                id bigint not null primary key,
                order_id bigint not null,
                status varchar(30) not null,
                pg_provider varchar(30) not null,
                payment_request_id varchar(100) not null,
                requested_amount bigint not null,
                payment_key varchar(100),
                pg_transaction_id varchar(100),
                approved_amount bigint,
                failure_reason varchar(500),
                completion_retry_count int not null,
                approved_at datetime(6),
                canceled_at datetime(6),
                last_failed_at datetime(6),
                created_at datetime(6) not null,
                updated_at datetime(6) not null,
                deleted_at datetime(6),
                unique key uk_payments_order_id (order_id)
            )
            """.trimIndent(),
        )
        jdbcTemplate.jdbcOperations.execute(
            """
            create table payment_events (
                id bigint not null auto_increment primary key,
                order_id bigint not null,
                payment_id bigint,
                event_type varchar(40) not null,
                pg_provider varchar(30) not null,
                payment_request_id varchar(100) not null,
                payment_key varchar(100),
                pg_transaction_id varchar(100),
                requested_amount bigint not null,
                approved_amount bigint,
                pg_status varchar(50),
                failure_reason varchar(500),
                raw_response_summary varchar(1000),
                created_at datetime(6) not null,
                updated_at datetime(6) not null,
                deleted_at datetime(6)
            )
            """.trimIndent(),
        )
    }
}

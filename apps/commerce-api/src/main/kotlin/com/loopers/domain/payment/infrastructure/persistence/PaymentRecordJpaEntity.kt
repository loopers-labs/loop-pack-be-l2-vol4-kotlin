package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.model.PaymentStatus
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@AttributeOverride(name = "id", column = Column(name = "payment_record_id"))
@Table(
    name = "payment_records",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_records_order_id", columnNames = ["order_id"]),
        UniqueConstraint(name = "uk_payment_records_external_transaction_key", columnNames = ["external_transaction_key"]),
    ],
    indexes = [
        Index(name = "idx_payment_records_status_created_at", columnList = "status, created_at"),
    ],
)
class PaymentRecordJpaEntity(
    @Column(name = "order_id", nullable = false)
    var orderId: Long,
    @Column(name = "external_transaction_key", unique = true)
    var externalTransactionKey: String? = null,
    @Convert(converter = PaymentStatusConverter::class)
    @Column(
        name = "status",
        nullable = false,
        columnDefinition = "TINYINT COMMENT 'REQUESTED=10, UNKNOWN=15, APPROVED=20, FAILED=30'",
    )
    var status: PaymentStatus,
    @Column(name = "failure_reason")
    var failureReason: String? = null,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: ZonedDateTime,
    @Column(name = "completed_at")
    var completedAt: ZonedDateTime? = null,
) : BaseEntity() {
    fun updateFrom(payment: PaymentModel) {
        orderId = payment.orderId
        externalTransactionKey = payment.externalTransactionKey
        status = payment.status
        failureReason = payment.failureReason
        requestedAt = payment.requestedAt
        completedAt = payment.completedAt
    }

    fun toDomain(): PaymentModel = PaymentModel(
        id = id,
        orderId = orderId,
        externalTransactionKey = externalTransactionKey,
        status = status,
        failureReason = failureReason,
        requestedAt = requestedAt,
        completedAt = completedAt,
    )

    companion object {
        fun fromDomain(payment: PaymentModel): PaymentRecordJpaEntity = PaymentRecordJpaEntity(
            orderId = payment.orderId,
            externalTransactionKey = payment.externalTransactionKey,
            status = payment.status,
            failureReason = payment.failureReason,
            requestedAt = payment.requestedAt,
            completedAt = payment.completedAt,
        )
    }
}

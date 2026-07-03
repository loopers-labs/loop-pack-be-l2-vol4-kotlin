package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByTransactionKeyAndDeletedAtIsNull(transactionKey: String): PaymentJpaEntity?

    fun findByOrderIdAndDeletedAtIsNull(orderId: Long): PaymentJpaEntity?

    fun findByOrderIdAndStatusInAndDeletedAtIsNull(
        orderId: Long,
        statuses: Collection<PaymentStatus>,
    ): PaymentJpaEntity?

    fun findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
        status: PaymentStatus,
        createdAt: ZonedDateTime,
    ): List<PaymentJpaEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update PaymentJpaEntity p
        set p.status = :targetStatus, p.reason = :reason
        where p.transactionKey = :transactionKey
          and p.status = :currentStatus
          and p.deletedAt is null
        """,
    )
    fun updateStatusIfCurrent(
        @Param("transactionKey") transactionKey: String,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("targetStatus") targetStatus: PaymentStatus,
        @Param("reason") reason: String?,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update PaymentJpaEntity p
        set p.transactionKey = :transactionKey,
            p.status = :targetStatus,
            p.reason = :reason
        where p.id = :id
          and p.status = :currentStatus
          and p.deletedAt is null
        """,
    )
    fun updatePgResultIfCurrent(
        @Param("id") id: Long,
        @Param("transactionKey") transactionKey: String,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("targetStatus") targetStatus: PaymentStatus,
        @Param("reason") reason: String?,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update PaymentJpaEntity p
        set p.status = :targetStatus, p.reason = :reason
        where p.id = :id
          and p.status = :currentStatus
          and p.deletedAt is null
        """,
    )
    fun updateStatusById(
        @Param("id") id: Long,
        @Param("currentStatus") currentStatus: PaymentStatus,
        @Param("targetStatus") targetStatus: PaymentStatus,
        @Param("reason") reason: String?,
    ): Int
}

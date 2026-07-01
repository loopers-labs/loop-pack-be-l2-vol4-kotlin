package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<PaymentModel, Long> {
    fun findByOrderId(orderId: Long): PaymentModel?

    fun findByTransactionKey(transactionKey: String): PaymentModel?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentModel p WHERE p.orderId = :orderId")
    fun findByOrderIdForUpdate(@Param("orderId") orderId: Long): PaymentModel?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentModel p WHERE p.transactionKey = :transactionKey")
    fun findByTransactionKeyForUpdate(@Param("transactionKey") transactionKey: String): PaymentModel?

    @Query("SELECT p FROM PaymentModel p WHERE p.status = :status AND p.createdAt < :threshold")
    fun findByStatusAndCreatedAtBefore(
        @Param("status") status: PaymentStatus,
        @Param("threshold") threshold: ZonedDateTime,
    ): List<PaymentModel>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """UPDATE PaymentModel p SET p.status = :to, p.failureReason = :reason, p.updatedAt = :now
           WHERE p.id = :id AND p.status = com.loopers.domain.payment.PaymentStatus.PENDING""",
    )
    fun compareAndSetStatus(
        @Param("id") id: Long,
        @Param("to") to: PaymentStatus,
        @Param("reason") reason: PaymentFailureReason?,
        @Param("now") now: ZonedDateTime,
    ): Int

    // Detached 엔티티 save(merge) 시 종결 상태를 PENDING 으로 덮어쓰는 Lost Update 방지 —
    // PENDING 인 건에 대해서만 폴링 추적 컬럼을 벌크 업데이트한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """UPDATE PaymentModel p SET p.pollAttempts = p.pollAttempts + 1, p.lastPolledAt = :now
           WHERE p.id = :id AND p.status = com.loopers.domain.payment.PaymentStatus.PENDING""",
    )
    fun incrementPollAttempts(
        @Param("id") id: Long,
        @Param("now") now: ZonedDateTime,
    ): Int
}

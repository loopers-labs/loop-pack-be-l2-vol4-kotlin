package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderIdAndDeletedAtIsNull(orderId: Long): Payment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select payment
          from Payment payment
         where payment.orderId = :orderId
           and payment.deletedAt is null
        """,
    )
    fun findByOrderIdForUpdate(@Param("orderId") orderId: Long): Payment?

    fun findTop100ByStatusAndCompletionRetryCountLessThanAndDeletedAtIsNullOrderByUpdatedAtAsc(
        status: PaymentStatus,
        completionRetryCount: Int,
    ): List<Payment>
}

package com.loopers.infrastructure.payment.repository

import com.loopers.infrastructure.payment.entity.PaymentEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByMemberIdAndId(memberId: Long, paymentId: Long): PaymentEntity?

    fun findByMemberIdAndIdempotencyKey(memberId: Long, idempotencyKey: String): PaymentEntity?

    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentEntity payment where payment.id = :paymentId")
    fun findByIdForUpdate(@Param("paymentId") paymentId: Long): PaymentEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentEntity payment where payment.transactionKey = :transactionKey")
    fun findByTransactionKeyForUpdate(@Param("transactionKey") transactionKey: String): PaymentEntity?
}

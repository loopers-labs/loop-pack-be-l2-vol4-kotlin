package com.loopers.domain.payment.infrastructure.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentRecordJpaRepository : JpaRepository<PaymentRecordJpaEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentRecordJpaEntity?
    fun findByExternalTransactionKey(externalTransactionKey: String): PaymentRecordJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRecordJpaEntity p where p.orderId = :orderId")
    fun findByOrderIdForUpdate(
        @Param("orderId") orderId: Long,
    ): PaymentRecordJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRecordJpaEntity p where p.externalTransactionKey = :externalTransactionKey")
    fun findByExternalTransactionKeyForUpdate(
        @Param("externalTransactionKey") externalTransactionKey: String,
    ): PaymentRecordJpaEntity?
}

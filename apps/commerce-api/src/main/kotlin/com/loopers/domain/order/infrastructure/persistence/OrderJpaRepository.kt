package com.loopers.domain.order.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): OrderJpaEntity?
}

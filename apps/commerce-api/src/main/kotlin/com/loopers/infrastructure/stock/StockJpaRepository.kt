package com.loopers.infrastructure.stock

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockJpaRepository : JpaRepository<StockEntity, Long> {
    fun findByProductId(productId: Long): StockEntity?
    fun findAllByProductIdIn(productIds: List<Long>): List<StockEntity>

    /**
     * 차감 경로 전용. product_id 단건을 PESSIMISTIC_WRITE 락(SELECT ... FOR UPDATE)으로 조회해
     * 동일 상품에 대한 동시 차감을 직렬화한다. product_id 에는 unique 인덱스(uk_stock_product_id)가
     * 있어 Record Lock 범위로 좁혀진다. 반드시 트랜잭션 안에서 호출해야 락이 유효하다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockEntity s where s.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): StockEntity?
}

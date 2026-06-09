package com.loopers.domain.stock

interface StockRepositoryPort {
    fun findByProductId(productId: Long): Stock?

    /** 차감 경로 전용 비관적 락 조회. 트랜잭션 안에서만 의미가 있다. */
    fun findByProductIdForUpdate(productId: Long): Stock?
    fun findAllByProductIdIn(productIds: List<Long>): List<Stock>
    fun save(stock: Stock): Stock
    fun delete(stock: Stock)
}

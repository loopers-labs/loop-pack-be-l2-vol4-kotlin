package com.loopers.infrastructure.stock

import com.loopers.domain.BaseEntity
import com.loopers.domain.stock.Stock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "stocks",
    indexes = [
        Index(name = "idx_stocks_product_id", columnList = "product_id"),
    ],
)
class StockJpaEntity(
    productId: Long,
    quantity: Int,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long = productId

    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
        protected set

    fun apply(stock: Stock) {
        quantity = stock.quantity
    }

    fun toDomain(): Stock {
        return Stock(
            id = id,
            productId = productId,
            quantity = quantity,
        )
    }

    companion object {
        fun from(stock: Stock): StockJpaEntity {
            return StockJpaEntity(
                productId = stock.productId,
                quantity = stock.quantity,
            )
        }
    }
}

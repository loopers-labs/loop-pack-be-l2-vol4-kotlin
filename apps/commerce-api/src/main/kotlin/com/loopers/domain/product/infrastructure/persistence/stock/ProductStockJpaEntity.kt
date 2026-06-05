package com.loopers.domain.product.infrastructure.persistence.stock

import com.loopers.domain.product.model.StockModel
import com.loopers.domain.product.vo.StockQuantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "product_stocks")
class ProductStockJpaEntity(
    @Id
    @Column(name = "product_id", nullable = false)
    var productId: Long,
    @Column(name = "left_stock", nullable = false)
    var leftStock: Long,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }

    fun updateFrom(stock: StockModel) {
        leftStock = stock.leftStock.value
    }

    fun toDomain(): StockModel = StockModel(
        productId = productId,
        leftStock = StockQuantity.of(leftStock),
    )

    companion object {
        fun fromDomain(stock: StockModel): ProductStockJpaEntity = ProductStockJpaEntity(
            productId = stock.productId,
            leftStock = stock.leftStock.value,
        )
    }
}

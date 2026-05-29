package com.loopers.domain.catalog

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stocks")
class ProductStock(
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long,

    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int,
) : BaseEntity() {
    init {
        if (stockQuantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 미만일 수 없습니다.")
    }

    fun add(quantity: Int) {
        validatePositive(quantity)
        stockQuantity += quantity
    }

    fun deduct(quantity: Int) {
        validatePositive(quantity)
        if (stockQuantity < quantity) throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
        stockQuantity -= quantity
    }

    fun restore(quantity: Int) {
        add(quantity)
    }

    private fun validatePositive(quantity: Int) {
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 변경 수량은 0보다 커야 합니다.")
    }
}

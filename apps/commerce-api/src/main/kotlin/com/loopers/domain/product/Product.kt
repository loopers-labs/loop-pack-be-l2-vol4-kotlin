package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Product(
    val id: Long? = null,
    val brandId: Long,
    name: String,
    description: String,
    price: ProductPrice,
    stock: Stock,
    likeCount: Int = 0,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var price: ProductPrice = price
        private set

    var stock: Stock = stock
        private set

    var likeCount: Int = likeCount
        private set

    init {
        validate(brandId = brandId, name = name, description = description, likeCount = likeCount)
    }

    fun validateStockDeductible(quantity: StockQuantity) {
        stock.validateDeductible(quantity)
    }

    fun validateLikeCountDecreasable() {
        if (likeCount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수가 될 수 없습니다.")
    }

    fun isSoldOut(): Boolean = stock.isEmpty()

    fun rename(name: String) {
        validateName(name)
        this.name = name
    }

    fun changeDescription(description: String) {
        validateDescription(description)
        this.description = description
    }

    fun changePrice(price: ProductPrice) {
        this.price = price
    }

    fun adjustStock(stock: Stock) {
        this.stock = stock
    }

    companion object {
        private fun validate(brandId: Long, name: String, description: String, likeCount: Int) {
            if (brandId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 브랜드 ID 입니다.")
            validateName(name)
            validateDescription(description)
            if (likeCount < 0) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수일 수 없습니다.")
        }

        private fun validateName(name: String) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        }

        private fun validateDescription(description: String) {
            if (description.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.")
        }
    }
}

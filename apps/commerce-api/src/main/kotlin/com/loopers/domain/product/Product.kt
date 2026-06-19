package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Product(
    val id: Long? = null,
    val brandId: Long,
    name: String,
    description: String,
    price: ProductPrice,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var price: ProductPrice = price
        private set

    init {
        validate(brandId = brandId, name = name, description = description)
    }

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

    companion object {
        private fun validate(brandId: Long, name: String, description: String) {
            if (brandId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 브랜드 ID 입니다.")
            validateName(name)
            validateDescription(description)
        }

        private fun validateName(name: String) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        }

        private fun validateDescription(description: String) {
            if (description.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.")
        }
    }
}

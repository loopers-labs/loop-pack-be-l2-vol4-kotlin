package com.loopers.product.domain

import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class ProductName(
    @Column(name = "name", nullable = false, length = 100)
    val value: String,
) {
    init {
        if (value.isBlank() || value.length > 100) {
            throw BadRequestException(ProductErrorCode.INVALID_PRODUCT_NAME)
        }
    }

    // 프로젝트 VO 규칙: toString은 원문 value 그대로
    override fun toString(): String = value
}

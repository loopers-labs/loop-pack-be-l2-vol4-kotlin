package com.loopers.domain.brand

import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class BrandName(
    @Column(name = "name", nullable = false, length = 50)
    val value: String,
) {
    init {
        if (value.isBlank() || value.length > 50) {
            throw BadRequestException(BrandErrorCode.INVALID_BRAND_NAME)
        }
    }

    // 프로젝트 VO 규칙: toString은 원문 value 그대로 (data class 기본 "BrandName(value=..)" 대신)
    override fun toString(): String = value
}

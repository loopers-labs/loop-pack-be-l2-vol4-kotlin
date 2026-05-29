package com.loopers.domain.brand

import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class BrandName(
    value: String,
) {
    @Column(name = "name", nullable = false, length = 50)
    var value: String = value
        private set

    init {
        if (value.isBlank() || value.length > 50) {
            throw BadRequestException(BrandErrorCode.INVALID_BRAND_NAME)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is BrandName && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value
}

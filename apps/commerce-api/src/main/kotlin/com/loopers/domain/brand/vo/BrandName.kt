package com.loopers.domain.brand.vo

import com.loopers.domain.brand.constant.BrandErrorMessages
import com.loopers.domain.brand.exception.InvalidBrandException

@JvmInline
value class BrandName private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): BrandName {
            validate(value)
            return BrandName(value)
        }

        private fun validate(value: String) {
            if (value.isBlank()) {
                throw InvalidBrandException(BrandErrorMessages.BRAND_NAME_MUST_NOT_BE_BLANK)
            }
        }
    }
}

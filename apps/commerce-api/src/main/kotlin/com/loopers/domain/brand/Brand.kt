package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Brand(
    val id: Long = 0L,
    name: String,
    description: String,
    logoImageUrl: String,
    isDeleted: Boolean = false,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var logoImageUrl: String = logoImageUrl
        private set

    var isDeleted: Boolean = isDeleted
        private set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand name must not be blank.")
        }
        if (description.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand description must not be blank.")
        }
        if (logoImageUrl.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand logo image url must not be blank.")
        }
    }

    fun ensureDisplayable() {
        if (isDeleted) {
            throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")
        }
    }
}

package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Like(
    val id: Long = 0L,
    val memberId: Long,
    val productId: Long,
) {
    init {
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
    }
}

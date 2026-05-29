package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Like(
    val id: Long? = null,
    val userId: Long,
    val productId: Long,
    active: Boolean = true,
) {
    var active: Boolean = active
        private set

    init {
        validate(userId = userId, productId = productId)
    }

    fun canCancel(): Boolean = active

    fun canActivate(): Boolean = !active

    companion object {
        private fun validate(userId: Long, productId: Long) {
            if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 유저 ID 입니다.")
            if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 상품 ID 입니다.")
        }
    }
}

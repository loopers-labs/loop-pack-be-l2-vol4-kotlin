package com.loopers.domain.like

data class Like(
    val id: Long = 0L,
    val userId: Long,
    val productId: Long,
) {
    init {
        require(userId > 0) { "회원 ID는 0보다 커야 합니다." }
        require(productId > 0) { "상품 ID는 0보다 커야 합니다." }
    }
}

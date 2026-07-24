package com.loopers.domain.ranking

data class RankedProductId(
    val productId: Long,
    val rank: Long,
) {
    init {
        require(productId > 0) { "상품 ID는 양수여야 합니다. productId=$productId" }
        require(rank > 0) { "순위는 1 이상이어야 합니다. rank=$rank" }
    }
}

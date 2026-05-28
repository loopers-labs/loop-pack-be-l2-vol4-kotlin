package com.loopers.domain.order

data class OrderItem(
    val productId: Long,
    val quantity: Int,
    val snapshotProductName: String,
    val snapshotPrice: Long,
    val snapshotBrandName: String,
) {
    init {
        require(productId > 0) { "상품 ID는 0보다 커야 합니다." }
        require(quantity >= 1) { "주문 수량은 1 이상이어야 합니다." }
        require(snapshotProductName.isNotBlank()) { "상품명 스냅샷은 비어 있을 수 없습니다." }
        require(snapshotPrice > 0) { "가격 스냅샷은 0보다 커야 합니다." }
        require(snapshotBrandName.isNotBlank()) { "브랜드명 스냅샷은 비어 있을 수 없습니다." }
    }

    fun subtotal(): Long = snapshotPrice * quantity
}

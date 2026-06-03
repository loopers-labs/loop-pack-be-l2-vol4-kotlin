package com.loopers.domain.product

data class Product(
    val id: Long = 0L,
    val name: String,
    val price: Long,
    val description: String,
    val brandId: Long,
) {
    init {
        require(name.isNotBlank()) { "상품 이름은 비어 있을 수 없습니다." }
        require(price > 0) { "상품 가격은 0보다 커야 합니다." }
        require(brandId > 0) { "브랜드 ID는 0보다 커야 합니다." }
    }

    fun update(name: String, price: Long, description: String): Product =
        copy(name = name, price = price, description = description)

    companion object {
        fun create(name: String, price: Long, description: String, brandId: Long): Product =
            Product(name = name, price = price, description = description, brandId = brandId)
    }
}

package com.loopers.domain.brand

data class Brand(
    val id: Long = 0L,
    val name: String,
    val description: String,
) {
    init {
        require(name.isNotBlank()) { "브랜드 이름은 비어 있을 수 없습니다." }
        require(description.isNotBlank()) { "브랜드 설명은 비어 있을 수 없습니다." }
    }

    fun update(name: String, description: String): Brand = copy(name = name, description = description)

    companion object {
        fun create(name: String, description: String): Brand = Brand(name = name, description = description)
    }
}

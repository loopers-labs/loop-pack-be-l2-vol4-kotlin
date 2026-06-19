package com.loopers.domain.catalog

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_detail_images")
class ProductDetailImage(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "image_url", nullable = false, length = 500)
    val imageUrl: String,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int,
) : BaseEntity() {
    init {
        if (imageUrl.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 상세 이미지 URL은 비어있을 수 없습니다.")
        if (sortOrder < 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 상세 이미지 순서는 0 미만일 수 없습니다.")
    }
}

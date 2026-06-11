package com.loopers.like.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// 좋아요 현재 상태(toggle). hard delete, (userId, productId) UK로 멱등성 보장.
@Entity
@Table(
    name = "product_like",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_like_user_product", columnNames = ["user_id", "product_id"]),
    ],
)
class ProductLike(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long = productId
}

package com.loopers.infrastructure.like

import com.loopers.domain.BaseEntity
import com.loopers.domain.like.Like
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    indexes = [
        Index(name = "idx_likes_user_id", columnList = "user_id"),
        Index(name = "idx_likes_product_id", columnList = "product_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_likes_user_product", columnNames = ["user_id", "product_id"]),
    ],
)
class LikeJpaEntity(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "product_id", nullable = false)
    val productId: Long = productId

    fun toDomain(): Like {
        return Like(
            id = id,
            userId = userId,
            productId = productId,
            active = deletedAt == null,
        )
    }
}

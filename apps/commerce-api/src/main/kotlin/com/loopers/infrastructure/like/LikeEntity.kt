package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_likes_user_id_product_id",
            columnNames = ["user_id", "product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_likes_product_id_user_id", columnList = "product_id, user_id"),
    ],
)
class LikeEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun toDomain(): Like = Like(id = id, userId = userId, productId = productId)

    companion object {
        fun from(like: Like): LikeEntity = LikeEntity(userId = like.userId, productId = like.productId)
    }
}

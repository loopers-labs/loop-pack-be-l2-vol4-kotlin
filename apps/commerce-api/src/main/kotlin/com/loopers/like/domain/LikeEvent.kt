package com.loopers.like.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

// 좋아요 토글 이력(append-only, UK 없음). 발생 시각 = BaseEntity.createdAt.
@Entity
@Table(name = "like_event")
class LikeEvent(
    userId: Long,
    productId: Long,
    action: LikeAction,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long = productId

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10, updatable = false)
    val action: LikeAction = action
}

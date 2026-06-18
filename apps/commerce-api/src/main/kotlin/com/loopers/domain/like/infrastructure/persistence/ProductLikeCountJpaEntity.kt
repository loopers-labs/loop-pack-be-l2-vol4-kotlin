package com.loopers.domain.like.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product_like_counts")
class ProductLikeCountJpaEntity(
    @Id
    @Column(name = "product_id", nullable = false)
    var productId: Long,
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
)

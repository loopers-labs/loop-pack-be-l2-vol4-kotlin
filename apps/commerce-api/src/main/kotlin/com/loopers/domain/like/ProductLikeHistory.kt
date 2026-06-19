package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_like_histories",
    indexes = [
        Index(
            name = "idx_product_like_histories_user_product_created_id",
            columnList = "user_id, product_id, created_at, id",
        ),
    ],
)
class ProductLikeHistory(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    val action: LikeAction,
) : BaseEntity()

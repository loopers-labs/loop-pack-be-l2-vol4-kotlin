package com.loopers.projection.product

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

@Entity
@Immutable
@Table(
    name = "product_like_counts",
    indexes = [
        Index(name = "idx_plc_brand_like", columnList = "brand_id, like_count DESC"),
    ],
)
class ProductLikeCountProjectionEntity(
    @Id
    @Column(name = "product_id")
    val productId: Long = 0,

    @Column(name = "brand_id", nullable = false)
    val brandId: Long = 0,

    @Column(name = "like_count", nullable = false)
    val likeCount: Int = 0,
)

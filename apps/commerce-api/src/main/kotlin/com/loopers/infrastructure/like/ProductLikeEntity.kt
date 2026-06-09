package com.loopers.infrastructure.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_like_member_id_product_id",
            columnNames = ["member_id", "product_id"],
        ),
    ],
)
class ProductLikeEntity(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "product_id", nullable = false)
    var productId: Long,
) : BaseEntity()

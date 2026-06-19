package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_like_cursors",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_like_cursors_user_product",
            columnNames = ["user_id", "product_id"],
        ),
    ],
)
class ProductLikeCursor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "last_history_id")
    var lastHistoryId: Long? = null,
) : BaseEntity() {
    fun moveTo(history: ProductLikeHistory) {
        lastHistoryId = history.id
    }
}

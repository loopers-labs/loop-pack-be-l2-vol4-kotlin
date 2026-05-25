package com.loopers.infrastructure.like

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "like_count",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_like_count_product_id",
            columnNames = ["product_id"],
        ),
    ],
)
class LikeCountEntity(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "count", nullable = false)
    var count: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun increment() {
        count += 1
    }

    fun decrement() {
        if (count > 0) {
            count -= 1
        }
    }
}

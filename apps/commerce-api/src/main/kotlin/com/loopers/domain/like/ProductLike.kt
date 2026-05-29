package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 좋아요 현재 상태(toggle) 레코드. UNLIKE 시 hard delete, 다시 LIKE 시 새 row INSERT.
 * (userId, productId) 유일성은 DB UK로 보장(멱등성). status/소프트삭제 없는 immutable 레코드라 도메인 메서드 없음.
 * BaseEntity 중 createdAt 만 의미 있고 updatedAt/deletedAt 은 dead column(수용). 이력은 별도 LikeEvent 책임(본 주차 비범위).
 */
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

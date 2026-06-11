package com.loopers.product.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.ConflictException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "product")
class Product(
    brandId: Long,
    name: ProductName,
    price: Money,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false, updatable = false)
    val brandId: Long = brandId

    @Embedded
    var name: ProductName = name
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = price
        private set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.ACTIVE
        private set

    fun update(name: ProductName, price: Money) {
        this.name = name
        this.price = price
    }

    /**
     * 상태 전이. 같은 상태로의 전이는 멱등(no-op), 허용되지 않은 전이는 예외.
     * DELETED 전이 시 deletedAt 를 audit 목적으로 함께 기록한다(상태 판단은 status 가 단일 출처).
     */
    fun transitionTo(target: ProductStatus) {
        if (status == target) {
            return
        }
        if (!status.canTransitionTo(target)) {
            throw ConflictException(ProductErrorCode.INVALID_PRODUCT_STATUS_TRANSITION)
        }
        status = target
        if (target == ProductStatus.DELETED) {
            delete()
        }
    }
}

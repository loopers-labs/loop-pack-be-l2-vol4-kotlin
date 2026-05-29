package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.support.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "brand",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_brand_name", columnNames = ["name"]),
    ],
)
class Brand(
    name: BrandName,
    description: String? = null,
) : BaseEntity() {
    @Embedded
    var name: BrandName = name
        private set

    @Column(name = "description", length = 500)
    var description: String? = description
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BrandStatus = BrandStatus.ACTIVE
        private set

    fun update(name: BrandName, description: String?) {
        this.name = name
        this.description = description
    }

    /**
     * 상태 전이. 같은 상태로의 전이는 멱등(no-op), 허용되지 않은 전이는 예외.
     * DELETED 전이 시 deletedAt 를 audit 목적으로 함께 기록한다(상태 판단은 status 가 단일 출처).
     */
    fun transitionTo(target: BrandStatus) {
        if (status == target) {
            return
        }
        if (!status.canTransitionTo(target)) {
            throw ConflictException(BrandErrorCode.INVALID_BRAND_STATUS_TRANSITION)
        }
        status = target
        if (target == BrandStatus.DELETED) {
            delete()
        }
    }
}

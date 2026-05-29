package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.Embedded
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
) : BaseEntity() {
    @Embedded
    var name: BrandName = name
        private set

    fun updateName(name: BrandName) {
        this.name = name
    }
}

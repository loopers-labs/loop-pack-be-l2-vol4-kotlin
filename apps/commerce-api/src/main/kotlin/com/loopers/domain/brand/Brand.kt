package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
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
    description: String? = null,
) : BaseEntity() {
    @Embedded
    var name: BrandName = name
        private set

    @Column(name = "description", length = 500)
    var description: String? = description
        private set

    fun update(name: BrandName, description: String?) {
        this.name = name
        this.description = description
    }
}

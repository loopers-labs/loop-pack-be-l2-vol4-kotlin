package com.loopers.domain.brand.infrastructure.persistence

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.vo.BrandName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class BrandJpaEntity(
    @Column(name = "brand_name", nullable = false)
    var brandName: String,
) : BaseEntity() {
    fun updateFrom(brand: BrandModel) {
        brandName = brand.name.value
        if (brand.deletedAtOrNull == null) {
            restore()
        } else {
            delete()
        }
    }

    fun toDomain(): BrandModel = BrandModel(
        id = id,
        name = BrandName.of(brandName),
        deletedAtOrNull = deletedAt,
    )

    companion object {
        fun fromDomain(brand: BrandModel): BrandJpaEntity = BrandJpaEntity(
            brandName = brand.name.value,
        )
    }
}

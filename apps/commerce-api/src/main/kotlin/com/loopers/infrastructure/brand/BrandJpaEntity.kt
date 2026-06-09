package com.loopers.infrastructure.brand

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class BrandJpaEntity(
    name: String,
    description: String,
    logoImageUrl: String?,
) : BaseEntity() {
    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", nullable = false, length = 500)
    var description: String = description
        protected set

    @Column(name = "logo_image_url", length = 500)
    var logoImageUrl: String? = logoImageUrl
        protected set

    fun updateFrom(brand: Brand) {
        name = brand.name
        description = brand.description
        logoImageUrl = brand.logoImageUrl
    }

    fun toDomain(): Brand {
        return Brand(
            id = id,
            name = name,
            description = description,
            logoImageUrl = logoImageUrl,
        )
    }

    companion object {
        fun from(brand: Brand): BrandJpaEntity {
            return BrandJpaEntity(
                name = brand.name,
                description = brand.description,
                logoImageUrl = brand.logoImageUrl,
            )
        }
    }
}

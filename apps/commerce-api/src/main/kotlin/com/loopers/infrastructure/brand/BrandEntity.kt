package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brand")
class BrandEntity(
    @Column(name = "name", nullable = false, unique = true)
    var name: String,

    @Column(name = "description", nullable = false)
    var description: String,
) : BaseEntity() {
    fun toDomain(): Brand = Brand(id = id, name = name, description = description)

    fun update(name: String, description: String) {
        this.name = name
        this.description = description
    }

    companion object {
        fun from(brand: Brand): BrandEntity = BrandEntity(name = brand.name, description = brand.description)
    }
}

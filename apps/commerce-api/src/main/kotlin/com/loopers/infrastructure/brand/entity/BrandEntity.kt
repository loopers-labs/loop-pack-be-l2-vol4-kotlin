package com.loopers.infrastructure.brand.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.model.Brand
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@SQLRestriction("is_deleted = false")
@Table(name = "brand")
class BrandEntity(
    @Column(nullable = false, unique = true)
    var name: String,

    @Column(nullable = false)
    var description: String,

    @Column(nullable = false)
    var logoImageUrl: String,

    @Column(nullable = false)
    var isDeleted: Boolean = false,
) : BaseEntity() {
    fun update(domain: Brand) {
        name = domain.name
        description = domain.description
        logoImageUrl = domain.logoImageUrl
        isDeleted = domain.isDeleted
    }
}

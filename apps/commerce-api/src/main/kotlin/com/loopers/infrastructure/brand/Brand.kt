package com.loopers.infrastructure.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brand")
class Brand(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var description: String,

    @Column(nullable = false)
    var logoImageUrl: String,

    @Column(nullable = false)
    var isDeleted: Boolean = false,
) : BaseEntity() {
    fun update(domain: com.loopers.domain.brand.Brand) {
        name = domain.name
        description = domain.description
        logoImageUrl = domain.logoImageUrl
        isDeleted = domain.isDeleted
    }
}

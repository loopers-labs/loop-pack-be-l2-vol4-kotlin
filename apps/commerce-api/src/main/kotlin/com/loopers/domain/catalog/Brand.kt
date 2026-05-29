package com.loopers.domain.catalog

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class Brand(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BrandStatus = BrandStatus.ACTIVE,
) : BaseEntity() {
    init {
        validateName(name)
    }

    fun changeName(name: String) {
        validateName(name)
        this.name = name
    }

    fun activate() {
        status = BrandStatus.ACTIVE
    }

    fun deactivate() {
        status = BrandStatus.INACTIVE
    }

    private fun validateName(name: String) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.")
    }
}

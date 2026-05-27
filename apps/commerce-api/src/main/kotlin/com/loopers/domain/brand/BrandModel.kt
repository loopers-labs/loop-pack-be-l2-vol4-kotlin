package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class BrandModel(
    name: String,
    description: String,
) : BaseEntity() {
    @Column(nullable = false, unique = true, length = 100)
    var name: String = name
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    init {
        validateName(name)
    }

    fun update(name: String, description: String) {
        validateName(name)
        this.name = name
        this.description = description
    }

    fun softDelete() {
        if (deletedAt == null) {
            val currentId = id
            if (currentId > 0 && !name.endsWith("_deleted_$currentId")) {
                name = "${name}_deleted_$currentId"
            }
        }
        delete()
    }

    fun isDeleted(): Boolean {
        return deletedAt != null
    }

    companion object {
        private fun validateName(name: String) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.")
            if (name.length > 100) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 100자를 초과할 수 없습니다.")
        }
    }
}

package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Brand(
    val id: Long? = null,
    name: String,
    description: String,
    logoImageUrl: String? = null,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var logoImageUrl: String? = logoImageUrl
        private set

    init {
        validate(name = name, description = description, logoImageUrl = logoImageUrl)
    }

    fun rename(name: String) {
        validateName(name)
        this.name = name
    }

    fun changeDescription(description: String) {
        validateDescription(description)
        this.description = description
    }

    fun changeLogoImageUrl(logoImageUrl: String?) {
        validateLogoImageUrl(logoImageUrl)
        this.logoImageUrl = logoImageUrl
    }

    companion object {
        private fun validate(name: String, description: String, logoImageUrl: String?) {
            validateName(name)
            validateDescription(description)
            validateLogoImageUrl(logoImageUrl)
        }

        private fun validateName(name: String) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.")
        }

        private fun validateDescription(description: String) {
            if (description.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.")
        }

        private fun validateLogoImageUrl(logoImageUrl: String?) {
            if (logoImageUrl != null && logoImageUrl.isBlank()) {
                throw CoreException(ErrorType.BAD_REQUEST, "브랜드 로고 이미지 URL은 비어있을 수 없습니다.")
            }
        }
    }
}

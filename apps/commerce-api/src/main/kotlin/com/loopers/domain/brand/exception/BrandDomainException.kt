package com.loopers.domain.brand.exception

open class BrandDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidBrandException(
    message: String,
) : BrandDomainException(message)

class BrandNotActiveException(
    brandId: Long,
) : BrandDomainException("활성 브랜드가 아닙니다. brandId=$brandId")

class BrandNotFoundException(
    brandId: Long,
) : BrandDomainException("브랜드를 찾을 수 없습니다. brandId=$brandId")

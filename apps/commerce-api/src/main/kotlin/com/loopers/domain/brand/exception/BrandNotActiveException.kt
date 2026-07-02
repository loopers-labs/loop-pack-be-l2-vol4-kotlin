package com.loopers.domain.brand.exception

class BrandNotActiveException(
    brandId: Long,
) : BrandDomainException("활성 브랜드가 아닙니다. brandId=$brandId")

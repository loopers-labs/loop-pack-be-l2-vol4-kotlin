package com.loopers.domain.brand.exception

class BrandNotFoundException(
    brandId: Long,
) : BrandDomainException("브랜드를 찾을 수 없습니다. brandId=$brandId")

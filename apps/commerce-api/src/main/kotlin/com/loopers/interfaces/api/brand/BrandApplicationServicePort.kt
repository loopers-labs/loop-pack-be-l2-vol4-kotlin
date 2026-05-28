package com.loopers.interfaces.api.brand

import com.loopers.domain.brand.Brand

interface BrandApplicationServicePort {
    fun getBrand(id: Long): Brand
}

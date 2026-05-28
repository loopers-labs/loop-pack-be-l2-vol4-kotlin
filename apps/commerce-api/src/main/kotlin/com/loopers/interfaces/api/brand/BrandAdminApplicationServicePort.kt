package com.loopers.interfaces.api.brand

import com.loopers.application.brand.CreateBrandCommand
import com.loopers.application.brand.UpdateBrandCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface BrandAdminApplicationServicePort {
    fun getBrand(id: Long): Brand
    fun getBrands(pageRequest: PageRequest): PageResult<Brand>
    fun createBrand(command: CreateBrandCommand): Brand
    fun updateBrand(command: UpdateBrandCommand): Brand
    fun deleteBrand(id: Long)
}

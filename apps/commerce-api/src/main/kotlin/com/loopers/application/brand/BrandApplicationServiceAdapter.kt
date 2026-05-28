package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import com.loopers.interfaces.api.brand.BrandAdminApplicationServicePort
import com.loopers.interfaces.api.brand.BrandApplicationServicePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandApplicationServiceAdapter(
    private val brandService: BrandService,
    private val productService: ProductService,
    private val likeService: LikeService,
) : BrandApplicationServicePort,
    BrandAdminApplicationServicePort {
    @Transactional(readOnly = true)
    override fun getBrand(id: Long): Brand = brandService.getById(id)

    @Transactional(readOnly = true)
    override fun getBrands(pageRequest: PageRequest): PageResult<Brand> = brandService.getAll(pageRequest)

    @Transactional
    override fun createBrand(command: CreateBrandCommand): Brand =
        brandService.create(name = command.name, description = command.description)

    @Transactional
    override fun updateBrand(command: UpdateBrandCommand): Brand =
        brandService.update(id = command.id, name = command.name, description = command.description)

    @Transactional
    override fun deleteBrand(id: Long) {
        val deletedProductIds = productService.deleteAllByBrandId(id)
        deletedProductIds.forEach { likeService.deleteAllByProductId(it) }
        brandService.delete(id)
    }
}

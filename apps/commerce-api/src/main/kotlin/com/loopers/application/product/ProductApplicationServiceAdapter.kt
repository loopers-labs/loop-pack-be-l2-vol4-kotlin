package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.product.ProductService
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.product.ProductApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductApplicationServiceAdapter(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
    private val likeCountQueryPort: LikeCountQueryPort,
) : ProductApplicationServicePort,
    ProductAdminApplicationServicePort {
    @Transactional(readOnly = true)
    override fun getProduct(id: Long): ProductDetail {
        val product = productService.getById(id)
        val brand = brandService.getById(product.brandId)
        val stock = productService.getStockByProductId(product.id)
        val likeCount = likeCountQueryPort.countByProductId(product.id)
        return ProductDetail.of(product, brand, stock, likeCount)
    }

    @Transactional(readOnly = true)
    override fun getProducts(brandId: Long?, pageRequest: PageRequest): PageResult<ProductSummary> {
        val products = if (brandId == null) {
            productService.getAll(pageRequest)
        } else {
            productService.getAllByBrandId(brandId, pageRequest)
        }
        val productIds = products.items.map { it.id }
        val likeCounts = likeCountQueryPort.countsByProductIds(productIds)
        val brandIds = products.items.map { it.brandId }.distinct()
        val brandsById = brandService.findAllByIds(brandIds).associateBy { it.id }
        val summaries = products.items.map { product ->
            val brand = brandsById[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            val stock = productService.getStockByProductId(product.id)
            ProductSummary.of(product, brand, stock, likeCounts[product.id] ?: 0L)
        }
        return PageResult(
            items = summaries,
            page = products.page,
            size = products.size,
            totalElements = products.totalElements,
            totalPages = products.totalPages,
        )
    }

    @Transactional
    override fun createProduct(command: CreateProductCommand): ProductDetail {
        val brand = brandService.getById(command.brandId)
        val (product, stock) = productService.create(
            name = command.name,
            price = command.price,
            description = command.description,
            brandId = command.brandId,
            quantity = command.quantity,
        )
        likeService.initializeForProduct(product.id)
        return ProductDetail.of(product, brand, stock, likeCount = 0L)
    }

    @Transactional
    override fun updateProduct(command: UpdateProductCommand): ProductDetail {
        val (updatedProduct, updatedStock) = productService.update(
            id = command.id,
            name = command.name,
            price = command.price,
            description = command.description,
            brandId = command.brandId,
            quantity = command.quantity,
        )
        val brand = brandService.getById(updatedProduct.brandId)
        val likeCount = likeCountQueryPort.countByProductId(updatedProduct.id)
        return ProductDetail.of(updatedProduct, brand, updatedStock, likeCount)
    }

    @Transactional
    override fun deleteProduct(id: Long) {
        productService.delete(id)
        likeService.deleteAllByProductId(id)
    }
}

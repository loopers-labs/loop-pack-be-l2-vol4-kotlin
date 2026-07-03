package com.loopers.domain.product.application

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.command.ProductRegisterCommand
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.application.command.ProductUpdateCommand
import com.loopers.domain.product.application.info.ProductDetailInfo
import com.loopers.domain.product.application.info.ProductInfo
import com.loopers.domain.product.application.info.ProductSummaryInfo
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.model.ProductModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.event.ProductViewedApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
    private val stockService: StockService,
    private val likeService: LikeService,
    private val applicationEventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { },
) {
    @Transactional
    fun registerProduct(command: ProductRegisterCommand): ProductInfo {
        brandService.getById(command.brandId)
        val product = productService.register(command)
        stockService.initialize(
            productId = product.id,
            leftStock = command.initialStock,
        )
        likeService.initializeCount(product.id)
        return ProductInfo.from(product)
    }

    @Transactional
    fun updateProduct(
        productId: Long,
        command: ProductUpdateCommand,
    ): ProductInfo = productService.update(productId, command).let { ProductInfo.from(it) }

    @Transactional
    fun deleteProduct(productId: Long) {
        productService.softDelete(productId)
    }

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): ProductDetailInfo {
        val product = productService.getById(productId)
        val brand = brandService.getById(product.brandId)
        val likeCount = likeService.countByProductId(product.id)
        applicationEventPublisher.publishEvent(ProductViewedApplicationEvent(product.id))
        return ProductDetailInfo.from(product, brand, likeCount)
    }

    fun findProducts(command: ProductSearchCommand): List<ProductSummaryInfo> =
        productService.findProducts(command).toSummaryInfos()

    private fun List<ProductModel>.toSummaryInfos(
        likeCounts: Map<Long, Long> = likeService.countByProductIds(map { it.id }.toSet()),
    ): List<ProductSummaryInfo> {
        val brandsById = brandService.getByIds(map { it.brandId }.toSet())
            .associateBy { it.id }
        return map { product ->
            val brand = brandsById[product.brandId] ?: throw CoreException(ErrorType.NOT_FOUND)
            ProductSummaryInfo.from(
                product = product,
                brandName = brand.name.value,
                likeCount = likeCounts[product.id] ?: 0L,
            )
        }
    }
}

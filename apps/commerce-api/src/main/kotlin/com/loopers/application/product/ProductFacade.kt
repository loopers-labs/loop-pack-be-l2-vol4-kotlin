package com.loopers.application.product

import com.loopers.application.brand.BrandApplicationService
import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.projection.product.ProductLikeCountCommandRepository
import com.loopers.projection.product.ProductLikeCountQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ProductFacade(
    private val productApplicationService: ProductApplicationService,
    private val brandApplicationService: BrandApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
    private val productLikeCountCommandRepository: ProductLikeCountCommandRepository,
) {
    @Transactional
    fun createProduct(
        brandId: Long,
        name: String,
        description: String,
        price: Long,
        initialStock: Int = 0,
    ): ProductInfo {
        val brand = brandApplicationService.getBrand(brandId)
        val product = productApplicationService.createProduct(
            brandId = brandId,
            name = name,
            description = description,
            price = ProductPrice(price),
        )
        val productId = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다.")
        val stock = stockApplicationService.createStock(
            productId = productId,
            initialQuantity = initialStock,
        )
        productLikeCountCommandRepository.createInitial(productId, brandId)
        return ProductInfo.from(product, brand.name, stock, likeCount = 0)
    }

    fun getProductDetail(productId: Long): ProductInfo {
        val product = productApplicationService.getProduct(productId)
        val brand = brandApplicationService.getBrand(product.brandId)
        val stock = stockApplicationService.getStock(productId)
        val likeCount = productLikeCountQueryRepository.findById(productId)
            .map { it.likeCount }
            .orElse(0)
        return ProductInfo.from(product, brand.name, stock, likeCount)
    }

    fun getProducts(condition: ProductSearchCondition): PageResult<ProductSummaryInfo> {
        condition.brandId?.let { brandApplicationService.getBrand(it) }

        val products = productApplicationService.getProducts(condition)
        val brandIds = products.items.map { it.brandId }.distinct()
        val productIds = products.items.map { product ->
            product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다.")
        }
        val brandMap = brandApplicationService.getBrands(brandIds)
            .associateBy { it.id }
        val stockMap = stockApplicationService.getStocks(productIds)
        val likeCountMap = productLikeCountQueryRepository.findByProductIdIn(productIds)
            .associate { it.productId to it.likeCount }

        val items = products.items.map { product ->
            val productId = product.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다.")
            val brand = brandMap[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다. id=${product.brandId}")
            val stock = stockMap[productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다. productId=$productId")
            ProductSummaryInfo.from(product, brand.name, stock, likeCountMap[productId] ?: 0)
        }
        return PageResult(
            items = items,
            page = products.page,
            size = products.size,
            totalElements = products.totalElements,
            totalPages = products.totalPages,
        )
    }

    @Transactional
    fun updateProduct(
        productId: Long,
        name: String,
        description: String,
        price: Long,
    ): ProductInfo {
        val product = productApplicationService.updateProduct(
            id = productId,
            name = name,
            description = description,
            price = ProductPrice(price),
        )
        val brand = brandApplicationService.getBrand(product.brandId)
        val stock = stockApplicationService.getStock(productId)
        val likeCount = productLikeCountQueryRepository.findById(productId)
            .map { it.likeCount }
            .orElse(0)
        return ProductInfo.from(product, brand.name, stock, likeCount)
    }

    @Transactional
    fun deleteProduct(productId: Long) {
        productApplicationService.deleteProduct(productId)
        stockApplicationService.deleteStock(productId)
        productLikeCountCommandRepository.deleteByProductId(productId)
    }
}

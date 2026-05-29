package com.loopers.application.catalog

import com.loopers.domain.catalog.Brand
import com.loopers.domain.catalog.BrandRepository
import com.loopers.domain.catalog.CatalogCommand
import com.loopers.domain.catalog.Product
import com.loopers.domain.catalog.ProductDetailImage
import com.loopers.domain.catalog.ProductDetailImageRepository
import com.loopers.domain.catalog.ProductRepository
import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.catalog.ProductStatsRepository
import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CatalogApplicationService(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val productStatsRepository: ProductStatsRepository,
    private val productDetailImageRepository: ProductDetailImageRepository,
) {
    @Transactional(readOnly = true)
    fun getBrandInfo(brandId: Long): CatalogInfo.BrandInfo =
        CatalogInfo.BrandInfo.from(getBrand(brandId))

    @Transactional
    fun createBrand(command: CatalogCommand.CreateBrand): CatalogInfo.BrandInfo {
        if (brandRepository.existsActiveName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        return brandRepository.save(Brand(name = command.name))
            .let(CatalogInfo.BrandInfo::from)
    }

    @Transactional
    fun updateBrand(brandId: Long, command: CatalogCommand.UpdateBrand): CatalogInfo.BrandInfo {
        val brand = getBrand(brandId)
        if (brand.name != command.name && brandRepository.existsActiveName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        brand.changeName(command.name)
        return CatalogInfo.BrandInfo.from(brandRepository.save(brand))
    }

    @Transactional
    fun activateBrand(brandId: Long): CatalogInfo.BrandInfo {
        val brand = getBrand(brandId)
        brand.activate()
        return CatalogInfo.BrandInfo.from(brandRepository.save(brand))
    }

    @Transactional
    fun deactivateBrand(brandId: Long): CatalogInfo.BrandInfo {
        val brand = getBrand(brandId)
        brand.deactivate()
        return CatalogInfo.BrandInfo.from(brandRepository.save(brand))
    }

    @Transactional
    fun deleteBrand(brandId: Long) {
        val brand = getBrand(brandId)
        brand.delete()
        brandRepository.save(brand)
    }

    @Transactional
    fun createProduct(command: CatalogCommand.CreateProduct): CatalogInfo.ProductInfo {
        getBrand(command.brandId)
        if (productRepository.existsActiveNameInBrand(command.brandId, command.name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 상품 이름입니다.")
        }
        val product = productRepository.save(
            Product(
                brandId = command.brandId,
                name = command.name,
                price = command.price,
            ),
        )
        productStockRepository.save(ProductStock(productId = product.id, stockQuantity = command.initialStock))
        productStatsRepository.save(ProductStats(productId = product.id, likeCount = 0))
        productDetailImageRepository.saveAll(
            command.detailImageUrls.mapIndexed { index, imageUrl ->
                ProductDetailImage(productId = product.id, imageUrl = imageUrl, sortOrder = index)
            },
        )
        return CatalogInfo.ProductInfo.from(product)
    }

    @Transactional
    fun updateProduct(productId: Long, command: CatalogCommand.UpdateProduct): CatalogInfo.ProductInfo {
        val product = getProduct(productId)
        if (product.name != command.name && productRepository.existsActiveNameInBrand(product.brandId, command.name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 상품 이름입니다.")
        }
        product.change(command.name, command.price)
        productDetailImageRepository.softDeleteByProductId(product.id)
        productDetailImageRepository.saveAll(
            command.detailImageUrls.mapIndexed { index, imageUrl ->
                ProductDetailImage(productId = product.id, imageUrl = imageUrl, sortOrder = index)
            },
        )
        return CatalogInfo.ProductInfo.from(productRepository.save(product))
    }

    @Transactional
    fun activateProduct(productId: Long): CatalogInfo.ProductInfo {
        val product = getProduct(productId)
        product.activate()
        return CatalogInfo.ProductInfo.from(productRepository.save(product))
    }

    @Transactional
    fun suspendProduct(productId: Long): CatalogInfo.ProductInfo {
        val product = getProduct(productId)
        product.suspend()
        return CatalogInfo.ProductInfo.from(productRepository.save(product))
    }

    @Transactional
    fun deleteProduct(productId: Long) {
        val product = getProduct(productId)
        product.delete()
        productRepository.save(product)
    }

    @Transactional
    fun addStock(command: CatalogCommand.ChangeStock) {
        val stock = getStock(command.productId)
        stock.add(command.quantity)
        productStockRepository.save(stock)
    }

    @Transactional
    fun deductStock(command: CatalogCommand.ChangeStock) {
        if (!productStockRepository.deductIfEnough(command.productId, command.quantity)) {
            throw CoreException(ErrorType.CONFLICT, "재고가 부족합니다.")
        }
    }

    @Transactional
    fun restoreStock(command: CatalogCommand.ChangeStock) {
        val stock = getStock(command.productId)
        stock.restore(command.quantity)
        productStockRepository.save(stock)
    }

    @Transactional
    fun increaseLikeCount(productId: Long) {
        val stats = getStats(productId)
        stats.increaseLikeCount()
        productStatsRepository.save(stats)
    }

    @Transactional
    fun decreaseLikeCount(productId: Long) {
        val stats = getStats(productId)
        stats.decreaseLikeCount()
        productStatsRepository.save(stats)
    }

    private fun getBrand(brandId: Long): Brand =
        brandRepository.findById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")

    private fun getProduct(productId: Long): Product =
        productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")

    private fun getStock(productId: Long): ProductStock =
        productStockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

    private fun getStats(productId: Long): ProductStats =
        productStatsRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품 통계를 찾을 수 없습니다.")
}

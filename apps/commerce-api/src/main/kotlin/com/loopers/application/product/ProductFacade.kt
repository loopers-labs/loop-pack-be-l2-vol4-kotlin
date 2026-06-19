package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.product.cache.ProductCacheService
import com.loopers.application.product.dto.AdminProductDetailInfo
import com.loopers.application.product.dto.ProductCreateCommand
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.product.service.ProductCatalogService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val inventoryService: InventoryService,
    private val productStatService: ProductStatService,
    private val productCatalogService: ProductCatalogService,
    private val productCacheService: ProductCacheService,
) {
    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        val cachedProducts = productCacheService.findList(command)

        if (cachedProducts != null) {
            productCacheService.refreshList(command)
            return cachedProducts
        }

        val productSummaries = productService.getProducts(command)
        productCacheService.saveList(command, productSummaries)

        return productSummaries
    }

    fun getProduct(productId: Long): ProductDetailInfo {
        productCacheService.findDetail(productId)?.let {
            return it
        }

        val productDetail = getProductDetailFromDatabase(productId)
        productCacheService.saveDetail(productId, productDetail)

        return productDetail
    }

    private fun getProductDetailFromDatabase(productId: Long): ProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val productStat = productStatService.getProductStat(productId = product.id, brandId = product.brandId)
        val productCatalog = productCatalogService.display(
            product = product,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog)
    }

    fun getProductForAdmin(productId: Long): AdminProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val inventory = inventoryService.getInventory(product.id)
        val productStat = productStatService.getProductStat(productId = product.id, brandId = product.brandId)
        val productCatalog = productCatalogService.displayForAdmin(
            product = product,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )

        return AdminProductDetailInfo.from(productCatalog)
    }

    @Transactional
    fun createProduct(command: ProductCreateCommand): AdminProductDetailInfo {
        val brand = brandService.getBrand(command.brandId)
        val product = productService.createProduct(command)
        val inventory = inventoryService.createInventory(productId = product.id, quantity = command.quantity)
        val productStat = productStatService.save(
            productStatService.emptyStat(productId = product.id, brandId = product.brandId),
        )
        val productCatalog = productCatalogService.displayForAdmin(
            product = product,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )

        return AdminProductDetailInfo.from(productCatalog)
    }

    @Transactional
    fun updateProduct(productId: Long, command: ProductUpdateCommand): AdminProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val inventory = inventoryService.getInventory(product.id)
        val updatedProduct = productService.updateProduct(product = product, command = command)
        val productStat = productStatService.getProductStat(productId = product.id, brandId = product.brandId)
        val productCatalog = productCatalogService.displayForAdmin(
            product = updatedProduct,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )

        productCacheService.evictDetail(productId)

        return AdminProductDetailInfo.from(productCatalog)
    }

    @Transactional
    fun deleteProduct(productId: Long) {
        val product = productService.getProduct(productId)
        brandService.getBrand(product.brandId)
        productService.deleteProduct(product)
        productCacheService.evictDetail(productId)
    }
}

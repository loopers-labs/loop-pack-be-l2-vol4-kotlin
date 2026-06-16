package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.inventory.InventoryService
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
) {
    @Transactional(readOnly = true)
    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productService.getProducts(command)
    }

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): ProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val productStat = productStatService.getProductStat(product.id)
        val productCatalog = productCatalogService.display(
            product = product,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog)
    }

    @Transactional(readOnly = true)
    fun getProductForAdmin(productId: Long): AdminProductDetailInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val inventory = inventoryService.getInventory(product.id)
        val productStat = productStatService.getProductStat(product.id)
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
        val productStat = productStatService.emptyStat(product.id)
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
        val productStat = productStatService.getProductStat(product.id)
        val productCatalog = productCatalogService.displayForAdmin(
            product = updatedProduct,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )

        return AdminProductDetailInfo.from(productCatalog)
    }

    @Transactional
    fun deleteProduct(productId: Long) {
        val product = productService.getProduct(productId)
        brandService.getBrand(product.brandId)
        productService.deleteProduct(product)
    }
}

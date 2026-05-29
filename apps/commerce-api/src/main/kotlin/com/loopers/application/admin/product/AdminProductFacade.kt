package com.loopers.application.admin.product

import com.loopers.application.brand.BrandService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.product.ProductService
import com.loopers.application.product.dto.ProductCreateCommand
import com.loopers.application.product.dto.ProductDetailInfo
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.product.ProductCatalogService
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val inventoryService: InventoryService,
    private val productStatService: ProductStatService,
    private val productCatalogService: ProductCatalogService,
) {
    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productService.getProducts(command)
    }

    fun getProduct(productId: Long): ProductDetailInfo {
        val product = productService.getDisplayableProduct(productId)
        val brand = brandService.getDisplayableBrand(product.brandId)
        val inventory = inventoryService.getInventory(product.id)
        val productStat = productStatService.getProductStat(product.id)
        val productCatalog = productCatalogService.display(
            product = product,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog, quantity = inventory.quantity)
    }

    @Transactional
    fun createProduct(command: ProductCreateCommand): ProductDetailInfo {
        val brand = brandService.getDisplayableBrand(command.brandId)
        val product = productService.createProduct(command)
        val inventory = inventoryService.createInventory(productId = product.id, quantity = command.quantity)
        val productStat = productStatService.emptyStat(product.id)
        val productCatalog = productCatalogService.display(
            product = product,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog, quantity = inventory.quantity)
    }

    fun updateProduct(productId: Long, command: ProductUpdateCommand): ProductDetailInfo {
        val product = productService.getDisplayableProduct(productId)
        val brand = brandService.getDisplayableBrand(product.brandId)
        val inventory = inventoryService.getInventory(product.id)
        val updatedProduct = productService.updateProduct(product = product, command = command)
        val productStat = productStatService.getProductStat(product.id)
        val productCatalog = productCatalogService.display(
            product = updatedProduct,
            brand = brand,
            productStat = productStat,
        )

        return ProductDetailInfo.from(productCatalog, quantity = inventory.quantity)
    }

    fun deleteProduct(productId: Long) {
        val product = productService.getDisplayableProduct(productId)
        brandService.getDisplayableBrand(product.brandId)
        productService.deleteProduct(product)
    }
}

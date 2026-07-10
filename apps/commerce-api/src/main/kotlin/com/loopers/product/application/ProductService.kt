package com.loopers.product.application

import com.loopers.brand.domain.BrandErrorCode
import com.loopers.brand.domain.BrandRepository
import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.product.domain.ProductSort
import com.loopers.product.domain.ProductStatus
import com.loopers.product.domain.event.ProductViewedEvent
import com.loopers.shared.domain.Cursor
import com.loopers.shared.domain.CursorPage
import com.loopers.shared.domain.Money
import com.loopers.support.error.NotFoundException
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val inventoryRepository: InventoryRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val productDetailReader: ProductDetailReader,
) {
    @Transactional
    fun register(command: ProductCreateCommand): ProductInfo {
        brandRepository.findActiveById(command.brandId)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        val product = productRepository.save(
            Product(
                brandId = command.brandId,
                name = ProductName(command.name),
                price = Money(command.price),
            ),
        )
        inventoryRepository.save(Inventory.createFor(product.id, command.stock))
        return ProductInfo.from(product)
    }

    @Transactional
    fun update(command: ProductUpdateCommand): ProductInfo {
        val product = productRepository.findActiveById(command.id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        product.update(ProductName(command.name), Money(command.price))
        return ProductInfo.from(product)
    }

    @Transactional
    fun delete(id: Long) {
        val product = productRepository.findActiveById(id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        product.transitionTo(ProductStatus.DELETED)
        archiveInventories(listOf(product.id))
    }

    /**
     * 브랜드 삭제 cascade: 해당 브랜드의 활성 상품을 일괄 soft delete 한다.
     * 호출 주체는 [com.loopers.brand.application.BrandFacade] (브랜드 삭제 use case와 조합).
     */
    @Transactional
    fun softDeleteByBrand(brandId: Long) {
        val products = productRepository.findActiveByBrandId(brandId)
        products.forEach { it.transitionTo(ProductStatus.DELETED) }
        archiveInventories(products.map { it.id })
    }

    private fun archiveInventories(productIds: List<Long>) {
        if (productIds.isEmpty()) {
            return
        }
        inventoryRepository.findAllByProductIdIn(productIds).forEach {
            it.delete()
            inventoryRepository.save(it)
        }
    }

    @Transactional(readOnly = true)
    fun list(sort: ProductSort, brandId: Long?, cursor: Cursor?, size: Int): CursorPage<ProductInfo> {
        val page = productRepository.findAll(sort, brandId, cursor, size)
        return CursorPage(page.content.map(ProductInfo::from), page.hasNext, page.nextCursor)
    }

    @Transactional(readOnly = true)
    fun getDetail(id: Long, userId: Long? = null): ProductDetailInfo {
        val detail = productDetailReader.read(id)
        eventPublisher.publishEvent(ProductViewedEvent(productId = detail.id, userId = userId))
        return detail
    }

    @Transactional(readOnly = true)
    fun getActiveProducts(productsCommands: List<ProductCheckCommand>): Map<Long, Product> {
        val products = productRepository.findAllActiveByIdIn(productsCommands.map { (id, _) -> id }).associateBy { it.id }
        productsCommands.forEach { (id, price) ->
            val product = products[id] ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
            product.verifyPrice(price)
        }
        return products
    }
}

@Component
class ProductDetailReader(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Cacheable(cacheNames = [CACHE_NAME], key = "#id", sync = true)
    fun read(id: Long): ProductDetailInfo {
        val product = productRepository.findActiveById(id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val brand = brandRepository.findActiveById(product.brandId)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        return ProductDetailInfo.of(product, brand.name.value)
    }

    companion object {
        const val CACHE_NAME = "product-detail"
    }
}

data class ProductCheckCommand(
    val id: Long,
    val price: Long,
)

data class ProductCreateCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
    val stock: Long,
)

data class ProductUpdateCommand(
    val id: Long,
    val name: String,
    val price: Long,
)

data class ProductInfo(
    val id: Long,
    val brandId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun from(product: Product): ProductInfo =
            ProductInfo(
                id = product.id,
                brandId = product.brandId,
                name = product.name.value,
                price = product.price.amount,
                likeCount = product.likeCount,
            )
    }
}

data class ProductDetailInfo(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun of(product: Product, brandName: String): ProductDetailInfo =
            ProductDetailInfo(
                id = product.id,
                brandId = product.brandId,
                brandName = brandName,
                name = product.name.value,
                price = product.price.amount,
                likeCount = product.likeCount,
            )
    }
}

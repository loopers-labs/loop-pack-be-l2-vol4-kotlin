package com.loopers.application.catalog

import com.loopers.domain.BaseEntity
import com.loopers.domain.catalog.Brand
import com.loopers.domain.catalog.BrandRepository
import com.loopers.domain.catalog.Product
import com.loopers.domain.catalog.ProductDetailImage
import com.loopers.domain.catalog.ProductDetailImageRepository
import com.loopers.domain.catalog.ProductRepository
import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.catalog.ProductStatsRepository
import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import java.util.concurrent.atomic.AtomicLong

class FakeCatalogRepositories {
    val brandRepository = FakeBrandRepository()
    val productRepository = FakeProductRepository()
    val stockRepository = FakeProductStockRepository()
    val statsRepository = FakeProductStatsRepository()
    val imageRepository = FakeProductDetailImageRepository()

    fun service() = CatalogApplicationService(
        brandRepository = brandRepository,
        productRepository = productRepository,
        productStockRepository = stockRepository,
        productStatsRepository = statsRepository,
        productDetailImageRepository = imageRepository,
    )
}

class FakeBrandRepository : BrandRepository {
    private val store = mutableMapOf<Long, Brand>()
    private val sequence = AtomicLong(1)

    override fun save(brand: Brand): Brand {
        assignIdIfNeeded(brand, sequence)
        store[brand.id] = brand
        return brand
    }

    override fun findById(brandId: Long): Brand? = store[brandId]?.takeIf { it.deletedAt == null }

    override fun existsActiveName(name: String): Boolean = store.values.any { it.deletedAt == null && it.name == name }
}

class FakeProductRepository : ProductRepository {
    private val store = mutableMapOf<Long, Product>()
    private val sequence = AtomicLong(1)

    override fun save(product: Product): Product {
        assignIdIfNeeded(product, sequence)
        store[product.id] = product
        return product
    }

    override fun findById(productId: Long): Product? = store[productId]?.takeIf { it.deletedAt == null }

    override fun existsActiveNameInBrand(brandId: Long, name: String): Boolean =
        store.values.any { it.deletedAt == null && it.brandId == brandId && it.name == name }
}

class FakeProductStockRepository : ProductStockRepository {
    private val store = mutableMapOf<Long, ProductStock>()
    private val sequence = AtomicLong(1)

    override fun save(stock: ProductStock): ProductStock {
        assignIdIfNeeded(stock, sequence)
        store[stock.productId] = stock
        return stock
    }

    override fun findByProductId(productId: Long): ProductStock? = store[productId]?.takeIf { it.deletedAt == null }

    override fun lockAllByProductIds(productIds: Collection<Long>): List<ProductStock> =
        productIds.sorted().mapNotNull { findByProductId(it) }

    override fun deductIfEnough(productId: Long, quantity: Int): Boolean {
        val stock = findByProductId(productId) ?: return false
        if (stock.stockQuantity < quantity) return false
        stock.deduct(quantity)
        return true
    }
}

class FakeProductStatsRepository : ProductStatsRepository {
    private val store = mutableMapOf<Long, ProductStats>()
    private val sequence = AtomicLong(1)

    override fun save(stats: ProductStats): ProductStats {
        assignIdIfNeeded(stats, sequence)
        store[stats.productId] = stats
        return stats
    }

    override fun findByProductId(productId: Long): ProductStats? = store[productId]?.takeIf { it.deletedAt == null }
}

class FakeProductDetailImageRepository : ProductDetailImageRepository {
    private val store = mutableMapOf<Long, MutableList<ProductDetailImage>>()
    private val sequence = AtomicLong(1)

    override fun saveAll(images: List<ProductDetailImage>): List<ProductDetailImage> {
        images.forEach {
            assignIdIfNeeded(it, sequence)
            store.getOrPut(it.productId) { mutableListOf() }.add(it)
        }
        return images
    }

    override fun findByProductId(productId: Long): List<ProductDetailImage> =
        store[productId].orEmpty().filter { it.deletedAt == null }.sortedBy { it.sortOrder }

    override fun softDeleteByProductId(productId: Long) {
        store[productId].orEmpty().forEach { it.delete() }
    }
}

private fun assignIdIfNeeded(entity: BaseEntity, sequence: AtomicLong) {
    if (entity.id == 0L) {
        idField.setLong(entity, sequence.getAndIncrement())
    }
}

private val idField = BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }

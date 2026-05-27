package com.loopers.application.like

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProductLikeCountEventHandlerTest {
    @DisplayName("좋아요 이벤트를 처리할 때,")
    @Nested
    inner class Handle {
        @DisplayName("좋아요 생성 이벤트면 상품 좋아요 수를 증가시킨다.")
        @Test
        fun incrementsProductLikeCount_whenLikeCreatedEventIsHandled() {
            // arrange
            val fixture = Fixture()

            // act
            fixture.eventHandler.handle(LikeCreatedEvent(productId = 10L))

            // assert
            assertThat(fixture.productRepository.findActiveById(10L)?.likeCount).isEqualTo(1)
        }

        @DisplayName("좋아요 삭제 이벤트면 상품 좋아요 수를 감소시킨다.")
        @Test
        fun decrementsProductLikeCount_whenLikeDeletedEventIsHandled() {
            // arrange
            val fixture = Fixture()
            fixture.eventHandler.handle(LikeCreatedEvent(productId = 10L))

            // act
            fixture.eventHandler.handle(LikeDeletedEvent(productId = 10L))

            // assert
            assertThat(fixture.productRepository.findActiveById(10L)?.likeCount).isEqualTo(0)
        }
    }

    private class Fixture {
        val productRepository = InMemoryProductRepository()
        private val stockRepository = InMemoryProductStockRepository()
        private val brandRepository = InMemoryBrandRepository()
        private val productService = ProductService(
            productRepository = productRepository,
            productStockRepository = stockRepository,
            brandRepository = brandRepository,
        )
        val eventHandler = ProductLikeCountEventHandler(productService)

        init {
            brandRepository.save(BrandModel(name = "Nike", description = "Brand").withId(1L))
            productRepository.save(
                ProductModel(
                    brandId = 1L,
                    name = "Air Max",
                    description = "Shoes",
                    price = BigDecimal("120000.00"),
                    stockQuantity = 10,
                ).withId(10L),
            )
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val products = mutableMapOf<Long, ProductModel>()

        override fun save(product: ProductModel): ProductModel {
            products[product.id] = product
            return product
        }

        override fun findActiveById(id: Long): ProductModel? {
            return products[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel> {
            return products.values.toList()
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }
    }

    private class InMemoryProductStockRepository : ProductStockRepository {
        override fun save(stock: ProductStockModel): ProductStockModel {
            return stock
        }

        override fun findByProductId(productId: Long): ProductStockModel? {
            return null
        }
    }

    private class InMemoryBrandRepository : BrandRepository {
        private val brands = mutableMapOf<Long, BrandModel>()

        override fun save(brand: BrandModel): BrandModel {
            brands[brand.id] = brand
            return brand
        }

        override fun findById(id: Long): BrandModel? {
            return brands[id]
        }

        override fun findActiveById(id: Long): BrandModel? {
            return brands[id]?.takeUnless { it.isDeleted() }
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }
    }
}

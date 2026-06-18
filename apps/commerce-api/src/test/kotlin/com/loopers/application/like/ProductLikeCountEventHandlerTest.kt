package com.loopers.application.like

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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
            assertThat(fixture.productCacheRepository.evictedDetailIds).containsExactly(10L)
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
            assertThat(fixture.productCacheRepository.evictedDetailIds).containsExactly(10L, 10L)
        }
    }

    private class Fixture {
        val productRepository = InMemoryProductRepository()
        val productCacheRepository = RecordingProductCacheRepository()
        val eventHandler = ProductLikeCountEventHandler(productRepository, productCacheRepository)

        init {
            productRepository.save(
                ProductModel(
                    brandId = 1L,
                    name = "Air Max",
                    description = "Shoes",
                    price = BigDecimal("120000.00"),
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

        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> {
            return PageImpl(products.values.toList(), pageable, products.size.toLong())
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }

        override fun incrementLikeCount(productId: Long) {
            findActiveById(productId)?.incrementLikeCount()
        }

        override fun decrementLikeCount(productId: Long) {
            findActiveById(productId)?.decrementLikeCount()
        }
    }

    private class RecordingProductCacheRepository : ProductCacheRepository {
        val evictedDetailIds = mutableListOf<Long>()

        override fun getDetail(productId: Long): ProductInfo? = null

        override fun putDetail(productId: Long, product: ProductInfo) = Unit

        override fun evictDetail(productId: Long) {
            evictedDetailIds.add(productId)
        }

        override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? = null

        override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) = Unit
    }
}

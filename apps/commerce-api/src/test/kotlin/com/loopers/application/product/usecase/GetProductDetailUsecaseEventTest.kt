package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal

class GetProductDetailUsecaseEventTest {
    @DisplayName("상품 상세 조회에 성공하면 ProductViewedEvent 를 발행한다.")
    @Test
    fun publishesProductViewedEvent_onSuccess() {
        // arrange
        val fixture = Fixture()

        // act
        fixture.usecase.execute(10L)

        // assert
        assertThat(fixture.eventPublisher.events).containsExactly(ProductViewedEvent(productId = 10L))
    }

    @DisplayName("존재하지 않는 상품을 조회하면 ProductViewedEvent 를 발행하지 않는다.")
    @Test
    fun doesNotPublishProductViewedEvent_onNotFound() {
        // arrange
        val fixture = Fixture()

        // act & assert
        assertThatThrownBy { fixture.usecase.execute(999L) }
            .isInstanceOf(CoreException::class.java)
            .extracting { (it as CoreException).errorType }
            .isEqualTo(ErrorType.NOT_FOUND)
        assertThat(fixture.eventPublisher.events).isEmpty()
    }

    private class Fixture {
        val eventPublisher = RecordingEventPublisher()
        val usecase = GetProductDetailUsecase(
            productRepository = InMemoryProductRepository(),
            brandRepository = InMemoryBrandRepository(),
            productStockRepository = InMemoryProductStockRepository(),
            productCacheRepository = NoopProductCacheRepository(),
            eventPublisher = eventPublisher,
        )
    }

    private class RecordingEventPublisher : ApplicationEventPublisher {
        val events = mutableListOf<Any>()

        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val product = ProductModel(
            brandId = 1L,
            name = "Air Max",
            description = "Shoes",
            price = BigDecimal("120000.00"),
        ).withId(10L)
        override fun save(product: ProductModel) = product
        override fun findActiveById(id: Long) = product.takeIf { id == 10L }
        override fun findActiveAllByIds(ids: List<Long>): List<ProductModel> = ids.mapNotNull { findActiveById(it) }
        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> =
            PageImpl(listOf(product), pageable, 1)
        override fun existsActiveById(id: Long) = id == 10L
        override fun incrementLikeCount(productId: Long) = Unit
        override fun decrementLikeCount(productId: Long) = Unit
    }

    private class InMemoryBrandRepository : BrandRepository {
        private val brand = BrandModel(name = "Nike", description = "Shoes brand").withId(1L)
        override fun save(brand: BrandModel) = brand
        override fun findById(id: Long) = brand.takeIf { id == 1L }
        override fun findActiveById(id: Long) = brand.takeIf { id == 1L }
        override fun existsActiveById(id: Long) = id == 1L
    }

    private class InMemoryProductStockRepository : ProductStockRepository {
        override fun save(stock: ProductStockModel) = stock
        override fun findByProductId(productId: Long): ProductStockModel? = ProductStockModel(productId = productId, quantity = 10)
        override fun findByProductIdForUpdate(productId: Long) = findByProductId(productId)
        override fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel> =
            productIds.mapNotNull { findByProductId(it) }
    }

    private class NoopProductCacheRepository : ProductCacheRepository {
        override fun getDetail(productId: Long): ProductInfo? = null
        override fun putDetail(productId: Long, product: ProductInfo) = Unit
        override fun evictDetail(productId: Long) = Unit
        override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? = null
        override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) = Unit
    }
}

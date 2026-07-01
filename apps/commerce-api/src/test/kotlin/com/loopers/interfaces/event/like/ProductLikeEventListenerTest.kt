package com.loopers.interfaces.event.like

import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.domain.product.model.ProductStat
import com.loopers.domain.product.repository.ProductStatRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductLikeEventListenerTest {
    @DisplayName("좋아요 이벤트를 처리하면 상품 좋아요 수를 증가시킨다")
    @Test
    fun increasesLikeCount() {
        val repository = FakeProductStatRepository()
        repository.save(ProductStat(productId = 10L, brandId = 1L, likeCount = 0L))
        val listener = ProductLikeEventListener(ProductStatService(repository))

        listener.handle(ProductLikeEvent.Like(memberId = 1L, productId = 10L, brandId = 1L))

        val productStat = repository.findByProductId(10L)
        assertThat(productStat?.likeCount).isEqualTo(1L)
    }

    @DisplayName("좋아요 취소 이벤트를 처리하면 상품 좋아요 수를 감소시킨다")
    @Test
    fun decreasesLikeCount() {
        val repository = FakeProductStatRepository()
        repository.save(ProductStat(productId = 10L, brandId = 1L, likeCount = 1L))
        val listener = ProductLikeEventListener(ProductStatService(repository))

        listener.handle(ProductLikeEvent.Unlike(memberId = 1L, productId = 10L, brandId = 1L))

        val productStat = repository.findByProductId(10L)
        assertThat(productStat?.likeCount).isEqualTo(0L)
    }

    @DisplayName("집계 처리에 실패하면 이벤트 처리 예외를 전파한다")
    @Test
    fun propagatesAggregationFailure() {
        val listener = ProductLikeEventListener(ProductStatService(FailingProductStatRepository()))

        assertThrows<CoreException> {
            listener.handle(ProductLikeEvent.Like(memberId = 1L, productId = 10L, brandId = 1L))
        }
    }

    private class FakeProductStatRepository : ProductStatRepository {
        private val productStats = mutableListOf<ProductStat>()

        override fun findByProductId(productId: Long): ProductStat? {
            return productStats.find { it.productId == productId }
        }

        override fun findByProductIdForUpdate(productId: Long): ProductStat? {
            return findByProductId(productId)
        }

        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
            return productStats.filter { it.productId in productIds }
        }

        override fun save(productStat: ProductStat): ProductStat {
            productStats.removeIf { it.productId == productStat.productId }
            productStats.add(productStat)
            return productStat
        }
    }

    private class FailingProductStatRepository : ProductStatRepository {
        override fun findByProductId(productId: Long): ProductStat? {
            return null
        }

        override fun findByProductIdForUpdate(productId: Long): ProductStat? {
            return ProductStat(productId = productId, brandId = 1L, likeCount = 0L)
        }

        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
            return emptyList()
        }

        override fun save(productStat: ProductStat): ProductStat {
            throw CoreException(ErrorType.INTERNAL_ERROR, "Product stat failed.")
        }
    }
}

package com.loopers.application.product

import com.loopers.domain.event.ProductLikedEvent
import com.loopers.domain.event.ProductUnlikedEvent
import com.loopers.domain.product.ProductRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProductLikeCountEventHandlerTest {
    private val productRepository: ProductRepository = mock()
    private val handler = ProductLikeCountEventHandler(productRepository)

    @DisplayName("ProductLikedEvent를 구독하면, 해당 상품의 like_count를 원자적으로 증가시킨다.")
    @Test
    fun increasesLikeCount_onLiked() {
        handler.onLiked(ProductLikedEvent(10L, 100L))

        verify(productRepository).increaseLikeCount(100L)
    }

    @DisplayName("ProductUnlikedEvent를 구독하면, 해당 상품의 like_count를 원자적으로 감소시킨다.")
    @Test
    fun decreasesLikeCount_onUnliked() {
        handler.onUnliked(ProductUnlikedEvent(10L, 100L))

        verify(productRepository).decreaseLikeCount(100L)
    }

    @DisplayName("like_count 갱신이 실패해도 예외를 전파하지 않는다. (결과적 일관성)")
    @Test
    fun doesNotPropagate_whenUpdateFails() {
        whenever(productRepository.increaseLikeCount(any())).doThrow(RuntimeException("DB down"))

        assertDoesNotThrow { handler.onLiked(ProductLikedEvent(10L, 100L)) }
    }
}

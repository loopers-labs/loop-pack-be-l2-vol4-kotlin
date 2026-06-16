package com.loopers.like.application

import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.like.domain.LikeErrorCode
import com.loopers.like.domain.ProductLike
import com.loopers.like.domain.ProductLikeRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.error.ForbiddenException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

class LikeServiceTest {
    private val productRepository: ProductRepository = mock()
    private val productLikeRepository: ProductLikeRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()
    private val likeService = LikeService(productRepository, productLikeRepository, eventPublisher)

    private fun product(): Product = Product(brandId = 1L, name = ProductName("상품"), price = Money(1000))

    @DisplayName("좋아요한 적 없는 상품에 좋아요하면, ProductLike를 저장하고 ProductLikedEvent를 발행한다.")
    @Test
    fun savesLikeAndPublishesEvent_whenNotLikedYet() {
        whenever(productRepository.findActiveById(1L)).thenReturn(product())
        whenever(productLikeRepository.existsByUserIdAndProductId(10L, 1L)).thenReturn(false)
        whenever(productLikeRepository.save(any())).thenAnswer { it.arguments[0] as ProductLike }

        likeService.like(10L, 1L)

        assertAll(
            { verify(productLikeRepository).save(any()) },
            { verify(eventPublisher).publishEvent(ProductLikedEvent(10L, 1L)) },
        )
    }

    @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, 저장도 이벤트 발행도 하지 않는다. (멱등)")
    @Test
    fun isNoOp_whenAlreadyLiked() {
        whenever(productRepository.findActiveById(1L)).thenReturn(product())
        whenever(productLikeRepository.existsByUserIdAndProductId(10L, 1L)).thenReturn(true)

        likeService.like(10L, 1L)

        assertAll(
            { verify(productLikeRepository, never()).save(any()) },
            { verify(eventPublisher, never()).publishEvent(any()) },
        )
    }

    @DisplayName("존재하지 않는 상품에 좋아요하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenLikeTargetProductDoesNotExist() {
        whenever(productRepository.findActiveById(1L)).thenReturn(null)

        val result = assertThrows<NotFoundException> { likeService.like(10L, 1L) }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND) },
            { verify(productLikeRepository, never()).save(any()) },
            { verify(eventPublisher, never()).publishEvent(any()) },
        )
    }

    @DisplayName("좋아요한 상품의 좋아요를 취소하면, ProductLike를 삭제하고 ProductUnlikedEvent를 발행한다.")
    @Test
    fun deletesLikeAndPublishesEvent_whenLiked() {
        whenever(productRepository.findActiveById(1L)).thenReturn(product())
        val productLike = ProductLike(userId = 10L, productId = 1L)
        whenever(productLikeRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(productLike)

        likeService.unlike(10L, 1L)

        assertAll(
            { verify(productLikeRepository).delete(productLike) },
            { verify(eventPublisher).publishEvent(ProductUnlikedEvent(10L, 1L)) },
        )
    }

    @DisplayName("좋아요한 적 없는 상품의 좋아요를 취소하면, 삭제도 이벤트 발행도 하지 않는다. (멱등)")
    @Test
    fun isNoOp_whenNotLiked() {
        whenever(productRepository.findActiveById(1L)).thenReturn(product())
        whenever(productLikeRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(null)

        likeService.unlike(10L, 1L)

        assertAll(
            { verify(productLikeRepository, never()).delete(any()) },
            { verify(eventPublisher, never()).publishEvent(any()) },
        )
    }

    @DisplayName("존재하지 않는 상품의 좋아요를 취소하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenUnlikeTargetProductDoesNotExist() {
        whenever(productRepository.findActiveById(1L)).thenReturn(null)

        val result = assertThrows<NotFoundException> { likeService.unlike(10L, 1L) }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND) },
            { verify(productLikeRepository, never()).delete(any()) },
            { verify(eventPublisher, never()).publishEvent(any()) },
        )
    }

    @DisplayName("본인의 좋아요 목록을 조회하면, ProductLike를 LikeInfo로 매핑해 반환한다.")
    @Test
    fun mapsLikesToInfo_whenRequesterIsOwner() {
        whenever(productLikeRepository.findAllByUserId(10L))
            .thenReturn(listOf(ProductLike(10L, 100L), ProductLike(10L, 101L)))

        val likes = likeService.findMine(10L, 10L)

        assertThat(likes.map { it.productId }).containsExactly(100L, 101L)
    }

    @DisplayName("본인이 아닌 다른 사용자의 좋아요 목록을 조회하면, FORBIDDEN 예외가 발생하고 조회하지 않는다.")
    @Test
    fun throwsForbidden_whenRequesterIsNotOwner() {
        val result = assertThrows<ForbiddenException> { likeService.findMine(10L, 99L) }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(LikeErrorCode.FORBIDDEN_LIKE_ACCESS) },
            { verify(productLikeRepository, never()).findAllByUserId(any()) },
        )
    }
}

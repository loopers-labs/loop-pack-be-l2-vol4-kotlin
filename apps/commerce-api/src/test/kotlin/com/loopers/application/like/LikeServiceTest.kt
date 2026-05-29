package com.loopers.application.like

import com.loopers.domain.like.LikeErrorCode
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor
import com.loopers.domain.shared.Money
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

class LikeServiceTest {
    private val productRepository: ProductRepository = mock()
    private val productLikeRepository: ProductLikeRepository = mock()
    private val likeService = LikeService(productRepository, productLikeRepository)

    private fun product(): Product = Product(brandId = 1L, name = ProductName("상품"), price = Money(1000))

    @DisplayName("좋아요한 적 없는 상품에 좋아요하면, ProductLike를 저장하고 likeCount를 1 증가시킨다.")
    @Test
    fun savesLikeAndIncrementsCount_whenNotLikedYet() {
        val product = product()
        whenever(productRepository.findActiveById(1L)).thenReturn(product)
        whenever(productLikeRepository.existsByUserIdAndProductId(10L, 1L)).thenReturn(false)
        whenever(productLikeRepository.save(any())).thenAnswer { it.arguments[0] as ProductLike }

        likeService.like(10L, 1L)

        assertAll(
            { verify(productLikeRepository).save(any()) },
            { assertThat(product.likeCount).isEqualTo(1) },
        )
    }

    @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, 저장도 증가도 하지 않는다. (멱등)")
    @Test
    fun isNoOp_whenAlreadyLiked() {
        val product = product()
        whenever(productRepository.findActiveById(1L)).thenReturn(product)
        whenever(productLikeRepository.existsByUserIdAndProductId(10L, 1L)).thenReturn(true)

        likeService.like(10L, 1L)

        assertAll(
            { verify(productLikeRepository, never()).save(any()) },
            { assertThat(product.likeCount).isEqualTo(0) },
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
        )
    }

    @DisplayName("좋아요한 상품의 좋아요를 취소하면, ProductLike를 삭제하고 likeCount를 1 감소시킨다.")
    @Test
    fun deletesLikeAndDecrementsCount_whenLiked() {
        val product = product()
        product.like()
        whenever(productRepository.findActiveById(1L)).thenReturn(product)
        val productLike = ProductLike(userId = 10L, productId = 1L)
        whenever(productLikeRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(productLike)

        likeService.unlike(10L, 1L)

        assertAll(
            { verify(productLikeRepository).delete(productLike) },
            { assertThat(product.likeCount).isEqualTo(0) },
        )
    }

    @DisplayName("좋아요한 적 없는 상품의 좋아요를 취소하면, 삭제도 감소도 하지 않는다. (멱등)")
    @Test
    fun isNoOp_whenNotLiked() {
        val product = product()
        whenever(productRepository.findActiveById(1L)).thenReturn(product)
        whenever(productLikeRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(null)

        likeService.unlike(10L, 1L)

        assertAll(
            { verify(productLikeRepository, never()).delete(any()) },
            { assertThat(product.likeCount).isEqualTo(0) },
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
        )
    }

    @DisplayName("본인의 좋아요 목록을 조회하면, content를 LikeInfo로 매핑하고 hasNext·nextCursor를 그대로 전달한다.")
    @Test
    fun mapsLikePageToInfo_whenRequesterIsOwner() {
        whenever(productLikeRepository.findAllByUserId(10L, null, 2)).thenReturn(
            CursorPage(
                content = listOf(ProductLike(10L, 100L), ProductLike(10L, 101L)),
                hasNext = true,
                nextCursor = IdCursor(5L),
            ),
        )

        val page = likeService.findMine(10L, 10L, null, 2)

        assertAll(
            { assertThat(page.content.map { it.productId }).containsExactly(100L, 101L) },
            { assertThat(page.hasNext).isTrue() },
            { assertThat(page.nextCursor).isEqualTo(IdCursor(5L)) },
        )
    }

    @DisplayName("본인이 아닌 다른 사용자의 좋아요 목록을 조회하면, FORBIDDEN 예외가 발생하고 조회하지 않는다.")
    @Test
    fun throwsForbidden_whenRequesterIsNotOwner() {
        val result = assertThrows<ForbiddenException> { likeService.findMine(10L, 99L, null, 2) }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(LikeErrorCode.FORBIDDEN_LIKE_ACCESS) },
            { verify(productLikeRepository, never()).findAllByUserId(any(), any(), any()) },
        )
    }
}

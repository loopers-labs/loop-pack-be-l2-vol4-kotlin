package com.loopers.domain.like.unit

import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.like.port.LikeRepository
import com.loopers.domain.like.support.LikeSteps.Companion.좋아요_도메인_생성
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LikeServiceTest {
    @Test
    fun `이미_좋아요한_상품이면_좋아요_등록은_멱등하다`() {
        val likeRepository = mockk<LikeRepository>()
        val likeService = LikeService(likeRepository)
        every { likeRepository.exists(1L, 2L) } returns true

        val like = likeService.like(userId = 1L, productId = 2L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(2L)
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요하지_않은_상품이면_좋아요를_저장한다`() {
        val likeRepository = mockk<LikeRepository>()
        val likeService = LikeService(likeRepository)
        every { likeRepository.exists(1L, 2L) } returns false
        every { likeRepository.save(any()) } returns 좋아요_도메인_생성(userId = 1L, productId = 2L)

        val like = likeService.like(userId = 1L, productId = 2L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(2L)
        verify(exactly = 1) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요_취소는_멱등하게_삭제를_위임한다`() {
        val likeRepository = mockk<LikeRepository>()
        val likeService = LikeService(likeRepository)
        every { likeRepository.delete(1L, 2L) } returns Unit

        likeService.unlike(userId = 1L, productId = 2L)

        verify(exactly = 1) { likeRepository.delete(1L, 2L) }
    }

    @Test
    fun `상품별_좋아요_수를_조회한다`() {
        val likeRepository = mockk<LikeRepository>()
        val likeService = LikeService(likeRepository)
        every { likeRepository.countByProductIds(setOf(1L, 2L)) } returns mapOf(1L to 3L)

        val counts = likeService.countByProductIds(setOf(1L, 2L))

        assertThat(counts).containsEntry(1L, 3L)
        assertThat(counts).doesNotContainKey(2L)
    }
}

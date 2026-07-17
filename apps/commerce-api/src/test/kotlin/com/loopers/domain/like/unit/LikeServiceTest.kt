package com.loopers.domain.like.unit

import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.like.port.LikeRepository
import com.loopers.domain.like.port.ProductLikeCountRepository
import com.loopers.support.event.LikeChangedApplicationEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class LikeServiceTest {
    @Test
    fun `이미_좋아요한_상품이면_좋아요_수_이벤트를_저장하지_않는다`() {
        val likeRepository = mockk<LikeRepository>()
        val productLikeCountRepository = mockk<ProductLikeCountRepository>(relaxed = true)
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val likeService = LikeService(likeRepository, productLikeCountRepository, applicationEventPublisher)
        every { likeRepository.save(any()) } returns 0

        val like = likeService.like(userId = 1L, productId = 2L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(2L)
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any()) }
    }

    @Test
    fun `좋아요하지_않은_상품이면_delta_1_좋아요_변경_이벤트를_발행한다`() {
        val likeRepository = mockk<LikeRepository>()
        val productLikeCountRepository = mockk<ProductLikeCountRepository>(relaxed = true)
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val eventSlot = slot<LikeChangedApplicationEvent>()
        val likeService = LikeService(likeRepository, productLikeCountRepository, applicationEventPublisher)
        every { likeRepository.save(any()) } returns 1

        val like = likeService.like(userId = 1L, productId = 2L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(2L)
        verify(exactly = 1) { likeRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(capture(eventSlot)) }
        assertLikeChangedEvent(eventSlot.captured, productId = 2L, userId = 1L, delta = 1)
    }

    @Test
    fun `좋아요_취소가_실제_삭제되면_delta_마이너스_1_좋아요_변경_이벤트를_발행한다`() {
        val likeRepository = mockk<LikeRepository>()
        val productLikeCountRepository = mockk<ProductLikeCountRepository>(relaxed = true)
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val eventSlot = slot<LikeChangedApplicationEvent>()
        val likeService = LikeService(likeRepository, productLikeCountRepository, applicationEventPublisher)
        every { likeRepository.delete(1L, 2L) } returns 1

        likeService.unlike(userId = 1L, productId = 2L)

        verify(exactly = 1) { likeRepository.delete(1L, 2L) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(capture(eventSlot)) }
        assertLikeChangedEvent(eventSlot.captured, productId = 2L, userId = 1L, delta = -1)
    }

    @Test
    fun `좋아요한_적_없는_상품_취소는_좋아요_수_이벤트를_저장하지_않는다`() {
        val likeRepository = mockk<LikeRepository>()
        val productLikeCountRepository = mockk<ProductLikeCountRepository>(relaxed = true)
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val likeService = LikeService(likeRepository, productLikeCountRepository, applicationEventPublisher)
        every { likeRepository.delete(1L, 2L) } returns 0

        likeService.unlike(userId = 1L, productId = 2L)

        verify(exactly = 0) { applicationEventPublisher.publishEvent(any()) }
    }

    @Test
    fun `상품별_좋아요_수를_집계_테이블에서_조회한다`() {
        val likeRepository = mockk<LikeRepository>()
        val productLikeCountRepository = mockk<ProductLikeCountRepository>()
        val applicationEventPublisher = mockk<ApplicationEventPublisher>()
        val likeService = LikeService(likeRepository, productLikeCountRepository, applicationEventPublisher)
        every { productLikeCountRepository.countByProductIds(setOf(1L, 2L)) } returns mapOf(1L to 3L)

        val counts = likeService.countByProductIds(setOf(1L, 2L))

        assertThat(counts).containsEntry(1L, 3L)
        assertThat(counts).doesNotContainKey(2L)
    }

    private fun assertLikeChangedEvent(
        event: LikeChangedApplicationEvent,
        productId: Long,
        userId: Long,
        delta: Int,
    ) {
        assertThat(event.productId).isEqualTo(productId)
        assertThat(event.userId).isEqualTo(userId)
        assertThat(event.delta).isEqualTo(delta)
        assertThat(event.occurredAt).isNotNull()
    }
}

package com.loopers.like.application

import com.loopers.outbox.domain.OutboxStatus
import com.loopers.outbox.infrastructure.OutboxEventJpaRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// outbox 적재는 본 트랜잭션 커밋 검증이 핵심 — 클래스 @Transactional 금지
@SpringBootTest
@ActiveProfiles("test")
class LikeOutboxIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val productRepository: ProductRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("좋아요를 등록하면, 같은 트랜잭션에서 outbox_event 에 ProductLikedEvent 가 INIT 상태로 적재된다.")
    @Test
    fun insertsOutboxEvent_whenLiked() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))

        likeService.like(userId = 1L, productId = product.id)

        val outboxEvents = outboxEventJpaRepository.findAll()
        assertAll(
            { assertThat(outboxEvents).hasSize(1) },
            { assertThat(outboxEvents[0].aggregateType).isEqualTo("PRODUCT") },
            { assertThat(outboxEvents[0].aggregateId).isEqualTo(product.id) },
            { assertThat(outboxEvents[0].eventType).isEqualTo("ProductLikedEvent") },
            { assertThat(outboxEvents[0].status).isEqualTo(OutboxStatus.INIT) },
            { assertThat(outboxEvents[0].payload).contains("\"eventId\"") },
        )
    }

    @DisplayName("좋아요를 취소하면, outbox_event 에 ProductUnlikedEvent 가 추가로 적재된다.")
    @Test
    fun insertsOutboxEvent_whenUnliked() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        likeService.like(userId = 1L, productId = product.id)

        likeService.unlike(userId = 1L, productId = product.id)

        val eventTypes = outboxEventJpaRepository.findAll().map { it.eventType }
        assertThat(eventTypes).containsExactlyInAnyOrder("ProductLikedEvent", "ProductUnlikedEvent")
    }
}

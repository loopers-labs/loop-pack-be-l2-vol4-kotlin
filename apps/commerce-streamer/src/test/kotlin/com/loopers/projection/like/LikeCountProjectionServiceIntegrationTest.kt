package com.loopers.projection.like

import com.loopers.projection.like.application.LikeCountProjectionCommand
import com.loopers.projection.like.application.LikeCountProjectionException
import com.loopers.projection.like.application.LikeCountProjectionService
import com.loopers.projection.like.application.LikeCountProjectionStatus
import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaId
import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaRepository
import com.loopers.projection.like.infrastructure.persistence.ProductLikeCountJpaEntity
import com.loopers.projection.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class LikeCountProjectionServiceIntegrationTest
    @Autowired
    constructor(
        private val likeCountProjectionService: LikeCountProjectionService,
        private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
        private val processedKafkaEventJpaRepository: ProcessedKafkaEventJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `좋아요_증가_이벤트는_집계값을_1_증가시키고_처리_이력을_저장한다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID, likeCount = 0))
            val eventId = UUID.randomUUID()

            val result = likeCountProjectionService.project(command(eventId = eventId, delta = 1))

            assertThat(result.status).isEqualTo(LikeCountProjectionStatus.APPLIED)
            assertThat(productLikeCountJpaRepository.findById(상품_ID)).hasValueSatisfying {
                assertThat(it.likeCount).isEqualTo(1)
            }
            assertThat(processedKafkaEventJpaRepository.existsById(processedEventId(eventId))).isTrue()
        }

        @Test
        fun `좋아요_감소_이벤트는_집계값을_1_감소시킨다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID, likeCount = 1))

            val result = likeCountProjectionService.project(command(delta = -1))

            assertThat(result.status).isEqualTo(LikeCountProjectionStatus.APPLIED)
            assertThat(productLikeCountJpaRepository.findById(상품_ID)).hasValueSatisfying {
                assertThat(it.likeCount).isZero()
            }
        }

        @Test
        fun `같은_consumer_group과_event_id는_중복_처리하지_않는다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID, likeCount = 0))
            val eventId = UUID.randomUUID()

            val first = likeCountProjectionService.project(command(eventId = eventId, delta = 1))
            val duplicate = likeCountProjectionService.project(command(eventId = eventId, delta = 1))

            assertThat(first.status).isEqualTo(LikeCountProjectionStatus.APPLIED)
            assertThat(duplicate.status).isEqualTo(LikeCountProjectionStatus.DUPLICATE)
            assertThat(productLikeCountJpaRepository.findById(상품_ID)).hasValueSatisfying {
                assertThat(it.likeCount).isEqualTo(1)
            }
            assertThat(processedKafkaEventJpaRepository.count()).isEqualTo(1)
        }

        @Test
        fun `집계_행이_없으면_처리_이력을_남기지_않고_재시도_가능하게_실패한다`() {
            val eventId = UUID.randomUUID()

            assertThrows<LikeCountProjectionException> {
                likeCountProjectionService.project(command(eventId = eventId, delta = 1))
            }

            assertThat(productLikeCountJpaRepository.findById(상품_ID)).isEmpty
            assertThat(processedKafkaEventJpaRepository.existsById(processedEventId(eventId))).isFalse()
        }

        @Test
        fun `집계값이_0이면_좋아요_감소_이벤트는_처리_이력을_남기지_않고_실패한다`() {
            productLikeCountJpaRepository.saveAndFlush(ProductLikeCountJpaEntity(productId = 상품_ID, likeCount = 0))
            val eventId = UUID.randomUUID()

            assertThrows<LikeCountProjectionException> {
                likeCountProjectionService.project(command(eventId = eventId, delta = -1))
            }

            assertThat(productLikeCountJpaRepository.findById(상품_ID)).hasValueSatisfying {
                assertThat(it.likeCount).isZero()
            }
            assertThat(processedKafkaEventJpaRepository.existsById(processedEventId(eventId))).isFalse()
        }

        private fun command(
            eventId: UUID = UUID.randomUUID(),
            delta: Int,
        ): LikeCountProjectionCommand =
            LikeCountProjectionCommand(
                eventId = eventId,
                consumerGroup = CONSUMER_GROUP,
                eventType = EVENT_TYPE,
                productId = 상품_ID,
                delta = delta,
            )

        private fun processedEventId(eventId: UUID): ProcessedKafkaEventJpaId =
            ProcessedKafkaEventJpaId(
                eventId = eventId,
                consumerGroup = CONSUMER_GROUP,
            )

        companion object {
            private const val 상품_ID = 10L
            private const val CONSUMER_GROUP = "commerce-streamer-like-count"
            private const val EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
        }
    }

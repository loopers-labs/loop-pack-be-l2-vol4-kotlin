package com.loopers.domain.coupon.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponIssueRequestService
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import com.loopers.domain.coupon.port.CouponIssueEventHandledRepository
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.domain.coupon.presentation.CouponIssueRequestConsumer
import com.loopers.domain.coupon.presentation.CouponIssueRequestKafkaEvent
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.support.event.CouponIssueRequestedPayload
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import io.mockk.every
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.support.SendResult
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(
    properties = [
        "commerce-events.outbox-relay.enabled=false",
        "commerce-events.coupon-issue-request.topic-name=coupon-recovery-requests",
        "commerce-events.coupon-issue-request.dlt-topic-name=coupon-recovery-failures",
        "spring.kafka.listener.auto-startup=false",
    ],
)
class CouponIssueDeadLetterRecoveryIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val couponService: CouponService,
        private val couponIssueRequestService: CouponIssueRequestService,
        private val couponIssueRequestRepository: CouponIssueRequestRepository,
        private val couponIssueEventHandledRepository: CouponIssueEventHandledRepository,
        private val outboxRepository: OutboxRepository,
        private val objectMapper: ObjectMapper,
        private val transactionManager: PlatformTransactionManager,
        private val databaseCleanUp: DatabaseCleanUp,
        @Qualifier("RECORD_RECOVERER")
        private val recordRecoverer: ConsumerRecordRecoverer,
        private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry,
    ) {
        @MockkBean(name = KafkaConfig.DLT_KAFKA_TEMPLATE, relaxed = true)
        private lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

        @BeforeEach
        fun setUp() {
            clearMocks(kafkaTemplate)
        }

        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `재시도를_소진하면_설정된_DLT로_보낸_뒤_별도_트랜잭션에서_FAILED로_확정한다`() {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(templateCommand())
            val request = couponIssueRequestService.requestIssue(user.id, template.id)
            val eventId = UUID.randomUUID()
            val payload = CouponIssueRequestedPayload(request.requestId, user.id, template.id)
            val record = couponRecord(eventId, request.id, template.id, payload)
            val published = slot<ProducerRecord<Any, Any>>()
            val sendResult = mockk<SendResult<Any, Any>>(relaxed = true)
            every { kafkaTemplate.send(capture(published)) } returns CompletableFuture.completedFuture(sendResult)

            TransactionTemplate(transactionManager).executeWithoutResult { transaction ->
                recordRecoverer.accept(record, IllegalStateException("temporary failure exhausted"))
                transaction.setRollbackOnly()
            }

            val recovered = couponIssueRequestRepository.findByRequestIdOrNull(request.requestId)
            assertThat(recovered?.status).isEqualTo(CouponIssueRequestStatus.FAILED)
            assertThat(recovered?.failureReason).contains("temporary failure exhausted")
            assertThat(couponIssueEventHandledRepository.exists(eventId, CouponIssueRequestConsumer.CONSUMER_GROUP))
                .isFalse()
            assertThat(outboxRepository.findPendingByType(CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name))
                .singleElement()
                .extracting<String> { it.topicName }
                .isEqualTo(REQUEST_TOPIC)
            assertThat(
                kafkaListenerEndpointRegistry.listenerContainers
                    .flatMap { it.containerProperties.topics?.toList().orEmpty() },
            ).contains(REQUEST_TOPIC)
            assertThat(published.captured.topic()).isEqualTo(DLT_TOPIC)
            assertThat(published.captured.key()).isEqualTo(template.id.toString())
            verify(exactly = 1) { kafkaTemplate.send(any<ProducerRecord<Any, Any>>()) }
        }

        @Test
        fun `존재하지_않는_요청은_DLT_발행_뒤_복구를_정상_종료한다`() {
            val published = slot<ProducerRecord<Any, Any>>()
            val sendResult = mockk<SendResult<Any, Any>>(relaxed = true)
            every { kafkaTemplate.send(capture(published)) } returns CompletableFuture.completedFuture(sendResult)
            val record = couponRecord(
                eventId = UUID.randomUUID(),
                aggregateId = 999_999L,
                couponTemplateId = 42L,
                payload = CouponIssueRequestedPayload(UUID.randomUUID(), 7L, 42L),
            )

            recordRecoverer.accept(record, IllegalStateException("retry exhausted"))

            assertThat(published.captured.topic()).isEqualTo(DLT_TOPIC)
            verify(exactly = 1) { kafkaTemplate.send(any<ProducerRecord<Any, Any>>()) }
        }

        @ParameterizedTest
        @EnumSource(
            value = CouponIssueRequestStatus::class,
            names = ["ISSUED", "FAILED"],
        )
        fun `이미_최종_상태인_요청은_DLT_발행_뒤_상태를_덮어쓰지_않는다`(
            finalStatus: CouponIssueRequestStatus,
        ) {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(templateCommand())
            val request = couponIssueRequestService.requestIssue(user.id, template.id)
            val finalized = when (finalStatus) {
                CouponIssueRequestStatus.ISSUED -> request.markIssued(999_999L)
                CouponIssueRequestStatus.FAILED -> request.markFailed("previous failure")
                else -> error("Unsupported test status: $finalStatus")
            }
            couponIssueRequestRepository.save(finalized)
            val published = slot<ProducerRecord<Any, Any>>()
            val sendResult = mockk<SendResult<Any, Any>>(relaxed = true)
            every { kafkaTemplate.send(capture(published)) } returns CompletableFuture.completedFuture(sendResult)
            val record = couponRecord(
                eventId = UUID.randomUUID(),
                aggregateId = request.id,
                couponTemplateId = template.id,
                payload = CouponIssueRequestedPayload(request.requestId, user.id, template.id),
            )

            recordRecoverer.accept(record, IllegalStateException("late recovery"))

            assertThat(couponIssueRequestRepository.findByRequestIdOrNull(request.requestId)?.status)
                .isEqualTo(finalStatus)
            assertThat(published.captured.topic()).isEqualTo(DLT_TOPIC)
            verify(exactly = 1) { kafkaTemplate.send(any<ProducerRecord<Any, Any>>()) }
        }

        private fun couponRecord(
            eventId: UUID,
            aggregateId: Long,
            couponTemplateId: Long,
            payload: CouponIssueRequestedPayload,
        ): ConsumerRecord<String, ByteArray> {
            val event = CouponIssueRequestKafkaEvent(
                eventId = eventId,
                eventType = CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name,
                aggregateType = "COUPON_ISSUE_REQUEST",
                aggregateId = aggregateId,
                payload = objectMapper.writeValueAsString(payload),
            )
            return ConsumerRecord(
                REQUEST_TOPIC,
                0,
                0L,
                couponTemplateId.toString(),
                objectMapper.writeValueAsBytes(event),
            )
        }

        private fun templateCommand(): CouponTemplateCommand =
            CouponTemplateCommand(
                name = "RECOVERY_COUPON",
                type = "FIXED",
                value = 1_000,
                minOrderAmount = 0,
                expiredAt = LocalDateTime.now().plusDays(7),
                totalQuantity = 1,
            )

        companion object {
            private const val REQUEST_TOPIC = "coupon-recovery-requests"
            private const val DLT_TOPIC = "coupon-recovery-failures"
        }
    }

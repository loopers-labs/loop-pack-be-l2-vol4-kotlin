package com.loopers.domain.coupon.integration

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponIssueRequestService
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.coupon.infrastructure.persistence.issued.IssuedCouponJpaRepository
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.support.outbox.relay.OutboxRelay
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.utils.DatabaseCleanUp
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    properties = [
        "commerce-events.outbox-relay.enabled=false",
        "spring.kafka.listener.auto-startup=true",
    ],
)
class CouponIssueKafkaEndToEndTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val couponService: CouponService,
        private val couponIssueRequestService: CouponIssueRequestService,
        private val couponIssueRequestRepository: CouponIssueRequestRepository,
        private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
        private val outboxRepository: OutboxRepository,
        private val outboxRelay: OutboxRelay,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `outbox에서_Kafka로_발행한_쿠폰_요청을_consumer가_발급한다`() {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(
                CouponTemplateCommand(
                    name = "KAFKA_E2E_COUPON",
                    type = "FIXED",
                    value = 1_000,
                    minOrderAmount = 0,
                    expiredAt = LocalDateTime.now().plusDays(7),
                    totalQuantity = 1,
                ),
            )
            val request = couponIssueRequestService.requestIssue(user.id, template.id)
            val outbox = outboxRepository
                .findPendingByType(CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name)
                .single()

            assertThat(outboxRelay.publishOnce()).isEqualTo(1)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                assertThat(couponIssueRequestRepository.findByRequestIdOrNull(request.requestId)?.status)
                    .isEqualTo(CouponIssueRequestStatus.ISSUED)
                assertThat(issuedCouponJpaRepository.count()).isEqualTo(1)
            }
            assertThat(outboxRepository.findByEventIdOrNull(outbox.eventId)?.status)
                .isEqualTo(OutboxEventStatus.PUBLISHED)
        }

        companion object {
            private val topicSuffix = UUID.randomUUID().toString()
            private val couponTopic = "coupon-issue-e2e-$topicSuffix"
            private val couponDltTopic = "$couponTopic.DLT"

            @JvmStatic
            @DynamicPropertySource
            fun kafkaProperties(registry: DynamicPropertyRegistry) {
                val bootstrapServers = KafkaTestContainer.bootstrapServers
                registry.add("spring.kafka.bootstrap-servers") { bootstrapServers }
                registry.add("spring.kafka.admin.properties.bootstrap.servers") { bootstrapServers }
                registry.add("spring.kafka.admin.auto-create") { true }
                registry.add("spring.kafka.consumer.auto-offset-reset") { "earliest" }
                registry.add("commerce-events.coupon-issue-request.topic-name") { couponTopic }
                registry.add("commerce-events.coupon-issue-request.dlt-topic-name") { couponDltTopic }
                registry.add("commerce-events.coupon-issue-request.partitions") { 1 }
                registry.add("commerce-events.coupon-issue-request.replicas") { 1 }
            }
        }
    }

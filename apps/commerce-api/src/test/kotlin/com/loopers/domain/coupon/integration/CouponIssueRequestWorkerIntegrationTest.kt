package com.loopers.domain.coupon.integration

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponIssueRequestService
import com.loopers.domain.coupon.application.service.CouponIssueRequestWorker
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.coupon.infrastructure.persistence.issued.IssuedCouponJpaRepository
import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import com.loopers.domain.coupon.port.CouponIssueEventHandledRepository
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.support.error.CoreException
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = ["commerce-events.outbox-relay.enabled=false"],
)
class CouponIssueRequestWorkerIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val couponService: CouponService,
        private val couponIssueRequestService: CouponIssueRequestService,
        private val couponIssueRequestWorker: CouponIssueRequestWorker,
        private val couponIssueRequestRepository: CouponIssueRequestRepository,
        private val couponIssueEventHandledRepository: CouponIssueEventHandledRepository,
        private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
        private val outboxRepository: OutboxRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `발급_요청은_PENDING_request와_coupon_issue_outbox를_저장한다`() {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(templateCommand(totalQuantity = 1))

            val request = couponIssueRequestService.requestIssue(user.id, template.id)

            assertThat(request.status).isEqualTo(CouponIssueRequestStatus.PENDING)
            val events = outboxRepository.findPendingByType(CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name)
            assertThat(events).hasSize(1)
            assertThat(events.single().payload).contains(request.requestId.toString())
        }

        @Test
        fun `같은_사용자와_템플릿의_중복_요청은_기존_request를_반환한다`() {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(templateCommand(totalQuantity = 2))

            val first = couponIssueRequestService.requestIssue(user.id, template.id)
            val duplicate = couponIssueRequestService.requestIssue(user.id, template.id)

            assertThat(duplicate.requestId).isEqualTo(first.requestId)
            assertThat(outboxRepository.findPendingByType(CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name))
                .hasSize(1)
        }

        @Test
        fun `동시에_같은_쿠폰_발급을_요청해도_하나의_request로_수렴한다`() {
            val user = userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(templateCommand(totalQuantity = 5))

            val results = executeConcurrently((1..8).toList()) {
                couponIssueRequestService.requestIssue(user.id, template.id).requestId
            }

            assertThat(results.mapNotNull { it.requestId }.toSet()).hasSize(1)
            assertThat(results.mapNotNull { it.error }).isEmpty()
            assertThat(outboxRepository.findPendingByType(CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name))
                .hasSize(1)
        }

        @Test
        fun `worker는_템플릿_수량을_초과해_쿠폰을_발급하지_않는다`() {
            val firstUser = userService.signUp(사용자_회원가입(loginId = "couponworker1", email = "cw1@example.com"))
            val secondUser = userService.signUp(사용자_회원가입(loginId = "couponworker2", email = "cw2@example.com"))
            val template = couponService.createTemplate(templateCommand(totalQuantity = 1))
            val firstRequest = couponIssueRequestService.requestIssue(firstUser.id, template.id)
            val secondRequest = couponIssueRequestService.requestIssue(secondUser.id, template.id)

            val firstStatus = couponIssueRequestWorker.process(
                eventId = UUID.randomUUID(),
                consumerGroup = CONSUMER_GROUP,
                eventType = EVENT_TYPE,
                requestId = firstRequest.requestId,
            )
            val secondStatus = couponIssueRequestWorker.process(
                eventId = UUID.randomUUID(),
                consumerGroup = CONSUMER_GROUP,
                eventType = EVENT_TYPE,
                requestId = secondRequest.requestId,
            )

            assertThat(listOf(firstStatus, secondStatus)).containsExactlyInAnyOrder(
                CouponIssueRequestStatus.ISSUED,
                CouponIssueRequestStatus.SOLD_OUT,
            )
            assertThat(issuedCouponJpaRepository.count()).isEqualTo(1)
        }

        @Test
        fun `worker는_같은_event_id를_이미_처리했다면_다른_request라도_다시_발급하지_않는다`() {
            val firstUser = userService.signUp(사용자_회원가입(loginId = "eventdup1", email = "eventdup1@example.com"))
            val secondUser = userService.signUp(사용자_회원가입(loginId = "eventdup2", email = "eventdup2@example.com"))
            val template = couponService.createTemplate(templateCommand(totalQuantity = 2))
            val firstRequest = couponIssueRequestService.requestIssue(firstUser.id, template.id)
            val secondRequest = couponIssueRequestService.requestIssue(secondUser.id, template.id)
            val eventId = UUID.randomUUID()

            val firstStatus = couponIssueRequestWorker.process(
                eventId = eventId,
                consumerGroup = CONSUMER_GROUP,
                eventType = EVENT_TYPE,
                requestId = firstRequest.requestId,
            )
            val duplicateStatus = couponIssueRequestWorker.process(
                eventId = eventId,
                consumerGroup = CONSUMER_GROUP,
                eventType = EVENT_TYPE,
                requestId = secondRequest.requestId,
            )

            assertThat(firstStatus).isEqualTo(CouponIssueRequestStatus.ISSUED)
            assertThat(duplicateStatus).isEqualTo(CouponIssueRequestStatus.PENDING)
            assertThat(couponIssueRequestRepository.findByRequestIdOrNull(secondRequest.requestId)?.status)
                .isEqualTo(CouponIssueRequestStatus.PENDING)
            assertThat(issuedCouponJpaRepository.count()).isEqualTo(1)
            assertThat(couponIssueEventHandledRepository.exists(eventId, CONSUMER_GROUP)).isTrue()
        }

        @Test
        fun `worker는_재시도_가능한_예외를_FAILED로_확정하지_않고_처리_이력도_남기지_않는다`() {
            val user = userService.signUp(사용자_회원가입())
            val request = couponIssueRequestRepository.save(
                CouponIssueRequestModel(
                    userId = user.id,
                    couponTemplateId = 999_999L,
                ),
            )
            val eventId = UUID.randomUUID()

            assertThrows<CoreException> {
                couponIssueRequestWorker.process(
                    eventId = eventId,
                    consumerGroup = CONSUMER_GROUP,
                    eventType = EVENT_TYPE,
                    requestId = request.requestId,
                )
            }

            assertThat(couponIssueRequestRepository.findByRequestIdOrNull(request.requestId)?.status)
                .isEqualTo(CouponIssueRequestStatus.PENDING)
            assertThat(couponIssueEventHandledRepository.exists(eventId, CONSUMER_GROUP)).isFalse()
        }

        private fun templateCommand(totalQuantity: Long): CouponTemplateCommand =
            CouponTemplateCommand(
                name = "ASYNC_COUPON",
                type = "FIXED",
                value = 1_000,
                minOrderAmount = 0,
                expiredAt = LocalDateTime.now().plusDays(7),
                totalQuantity = totalQuantity,
            )

        private fun <T> executeConcurrently(
            targets: List<T>,
            action: (T) -> UUID,
        ): List<CouponIssueAttemptResult> {
            val executor = Executors.newFixedThreadPool(targets.size)
            val ready = CountDownLatch(targets.size)
            val start = CountDownLatch(1)
            try {
                val futures = targets.map { target ->
                    executor.submit<CouponIssueAttemptResult> {
                        ready.countDown()
                        start.await()
                        try {
                            CouponIssueAttemptResult(requestId = action(target))
                        } catch (e: Exception) {
                            CouponIssueAttemptResult(error = e::class.simpleName)
                        }
                    }
                }
                assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                return futures.map { it.get(10, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }

        companion object {
            private const val CONSUMER_GROUP = "commerce-api-coupon-issue"
            private const val EVENT_TYPE = "COUPON_ISSUE_REQUESTED_V1"
        }
    }

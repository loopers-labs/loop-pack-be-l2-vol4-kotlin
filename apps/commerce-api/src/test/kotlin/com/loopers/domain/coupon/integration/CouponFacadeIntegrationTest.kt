package com.loopers.domain.coupon.integration

import com.loopers.domain.coupon.application.CouponFacade
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.coupon.exception.CouponNotIssuableException
import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.model.IssuedCouponDisplayStatus
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.domain.coupon.port.IssuedCouponRepository
import com.loopers.domain.coupon.support.CouponSteps.Companion.쿠폰템플릿_도메인_생성
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
class CouponFacadeIntegrationTest
    @Autowired
    constructor(
        private val couponFacade: CouponFacade,
        private val couponService: CouponService,
        private val couponTemplateRepository: CouponTemplateRepository,
        private val issuedCouponRepository: IssuedCouponRepository,
        private val entityManagerFactory: EntityManagerFactory,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `내_쿠폰_목록_조회는_템플릿을_쿠폰수만큼_조회하지_않는다`() {
            val userId = 1L
            val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            repeat(3) { index ->
                val template = couponService.createTemplate(쿠폰템플릿_생성_커맨드(name = "WELCOME_$index"))
                couponService.issue(userId, template.id)
            }

            statistics.clear()
            val coupons = couponFacade.findMyCoupons(userId)

            assertThat(coupons).hasSize(3)
            assertThat(coupons.map { it.templateId }).doesNotHaveDuplicates()
            assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(2L)
        }

        @Test
        fun `같은_사용자가_같은_템플릿으로_동시에_발급해도_단_한_건만_저장되고_나머지는_충돌한다`() {
            val userId = 1L
            val template = couponService.createTemplate(쿠폰템플릿_생성_커맨드())
            val threadCount = 10

            val results = 동시에_발급(threadCount) { couponService.issue(userId, template.id) }

            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.count { it.isDuplicate }).isEqualTo(threadCount - 1)
            assertThat(issuedCouponRepository.findByUserId(userId)).hasSize(1)
        }

        @Test
        fun `만료된_템플릿으로_발급된_쿠폰은_내_쿠폰_목록에서_EXPIRED로_표시된다`() {
            val userId = 1L
            val expiredTemplate = couponTemplateRepository.save(
                쿠폰템플릿_도메인_생성(
                    id = 0L,
                    name = "EXPIRED_10",
                    expiredAt = LocalDateTime.now().minusDays(1),
                ),
            )
            issuedCouponRepository.save(
                IssuedCouponModel.issue(
                    userId = userId,
                    couponTemplateId = expiredTemplate.id,
                    now = LocalDateTime.now().minusDays(2),
                ),
            )

            val coupons = couponFacade.findMyCoupons(userId)

            assertThat(coupons).hasSize(1)
            assertThat(coupons.first().displayStatus).isEqualTo(IssuedCouponDisplayStatus.EXPIRED)
        }

        @Test
        fun `만료된_템플릿은_발급할_수_없다`() {
            val userId = 1L
            val expiredTemplate = couponTemplateRepository.save(
                쿠폰템플릿_도메인_생성(
                    id = 0L,
                    name = "EXPIRED_10",
                    expiredAt = LocalDateTime.now().minusDays(1),
                ),
            )

            assertThatThrownBy { couponService.issue(userId, expiredTemplate.id) }
                .isInstanceOf(CouponNotIssuableException::class.java)
        }

        private fun 동시에_발급(
            threadCount: Int,
            action: () -> Unit,
        ): List<IssueAttemptResult> {
            val executor = Executors.newFixedThreadPool(threadCount)
            val ready = CountDownLatch(threadCount)
            val start = CountDownLatch(1)

            try {
                val futures = (1..threadCount).map {
                    executor.submit<IssueAttemptResult> {
                        ready.countDown()
                        start.await()
                        try {
                            action()
                            IssueAttemptResult(isSuccess = true)
                        } catch (e: DuplicateIssuedCouponException) {
                            IssueAttemptResult(isDuplicate = true)
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

        private fun 쿠폰템플릿_생성_커맨드(
            name: String = "WELCOME_10",
        ): CouponTemplateCommand = CouponTemplateCommand(
            name = name,
            type = "RATE",
            value = 10,
            minOrderAmount = 10_000,
            expiredAt = LocalDateTime.now().plusDays(7),
        )

        private data class IssueAttemptResult(
            val isSuccess: Boolean = false,
            val isDuplicate: Boolean = false,
        )
    }

package com.loopers.domain.coupon.integration

import com.loopers.domain.coupon.application.CouponFacade
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
class CouponFacadeIntegrationTest
    @Autowired
    constructor(
        private val couponFacade: CouponFacade,
        private val couponService: CouponService,
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

        private fun 쿠폰템플릿_생성_커맨드(
            name: String = "WELCOME_10",
        ): CouponTemplateCommand = CouponTemplateCommand(
            name = name,
            type = "RATE",
            value = 10,
            minOrderAmount = 10_000,
            expiredAt = LocalDateTime.now().plusDays(7),
        )
    }

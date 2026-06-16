package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.coupon.CouponIssueRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.DiscountType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class CouponIssueRepositoryIntegrationTest @Autowired constructor(
    private val couponIssueRepository: CouponIssueRepository,
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save")
    @Nested
    inner class Save {
        @DisplayName("발급 쿠폰을 저장한다")
        @Test
        fun savesCouponIssue() {
            val issue = createCouponIssue()

            val result = couponIssueRepository.save(issue)

            val savedEntity = couponIssueJpaRepository.findAll().single()
            assertAll(
                { assertThat(result.id).isPositive() },
                { assertThat(result.memberId).isEqualTo(issue.memberId) },
                { assertThat(result.couponId).isEqualTo(issue.couponId) },
                { assertThat(result.status).isEqualTo(issue.status) },
                { assertThat(result.type).isEqualTo(issue.type) },
                { assertThat(result.discountValue).isEqualTo(issue.discountValue) },
                { assertThat(result.minOrderAmount).isEqualTo(issue.minOrderAmount) },
                { assertThat(result.expiredAt.toInstant()).isEqualTo(issue.expiredAt.toInstant()) },
                { assertThat(savedEntity.memberId).isEqualTo(issue.memberId) },
                { assertThat(savedEntity.couponId).isEqualTo(issue.couponId) },
                { assertThat(savedEntity.status).isEqualTo(issue.status) },
                { assertThat(savedEntity.type).isEqualTo(issue.type) },
                { assertThat(savedEntity.discountValue).isEqualTo(issue.discountValue) },
            )
        }
    }

    @DisplayName("findById")
    @Nested
    inner class FindById {
        @DisplayName("저장된 발급 쿠폰을 조회한다")
        @Test
        fun returnsCouponIssue() {
            val saved = couponIssueJpaRepository.save(CouponIssueMapper.toEntity(createCouponIssue()))

            val result = couponIssueRepository.findById(saved.id)

            assertAll(
                { assertThat(result?.id).isEqualTo(saved.id) },
                { assertThat(result?.memberId).isEqualTo(saved.memberId) },
                { assertThat(result?.couponId).isEqualTo(saved.couponId) },
                { assertThat(result?.status).isEqualTo(saved.status) },
            )
        }
    }

    private fun createCouponIssue(): CouponIssue {
        return CouponIssue(
            memberId = 1L,
            couponId = 10L,
            status = CouponIssueStatus.AVAILABLE,
            type = DiscountType.RATE,
            discountValue = 10L,
            minOrderAmount = 10_000L,
            expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
            usedAt = null,
        )
    }
}

package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class UserCouponRepositoryAdapterIntegrationTest @Autowired constructor(
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val issuedAt = LocalDateTime.parse("2026-06-07T10:00:00")

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("발급 쿠폰을 저장하면, id가 부여되고 AVAILABLE 상태로 복원된다.")
        @Test
        fun insertsAvailableCoupon() {
            val saved = userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 1L, userId = 9L, issuedAt = issuedAt),
            )

            assertThat(saved.id).isGreaterThan(0L)
            val found = userCouponRepositoryPort.findById(saved.id)
            assertThat(found).isNotNull
            assertThat(found?.couponTemplateId).isEqualTo(1L)
            assertThat(found?.userId).isEqualTo(9L)
            assertThat(found?.status).isEqualTo(CouponStatus.AVAILABLE)
            assertThat(found?.issuedAt).isEqualTo(issuedAt)
            assertThat(found?.usedAt).isNull()
        }
    }

    @DisplayName("existsByUserIdAndCouponTemplateId를 호출할 때, ")
    @Nested
    inner class Exists {
        @DisplayName("동일 사용자·템플릿의 발급 쿠폰이 있으면 true를 반환한다.")
        @Test
        fun returnsTrue_whenIssued() {
            userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 1L, userId = 9L, issuedAt = issuedAt),
            )

            assertThat(userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(9L, 1L)).isTrue()
        }

        @DisplayName("사용자 또는 템플릿이 다르면 false를 반환한다.")
        @Test
        fun returnsFalse_whenNotIssued() {
            userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 1L, userId = 9L, issuedAt = issuedAt),
            )

            assertThat(userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(9L, 2L)).isFalse()
            assertThat(userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(8L, 1L)).isFalse()
        }
    }

    @DisplayName("findAllByUserId를 호출할 때, ")
    @Nested
    inner class FindAllByUserId {
        @DisplayName("해당 사용자의 발급 쿠폰만 최근 발급순(id 내림차순)으로 반환한다.")
        @Test
        fun returnsOnlyOwnCouponsInDescOrder() {
            val first = userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 1L, userId = 9L, issuedAt = issuedAt),
            )
            val second = userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 2L, userId = 9L, issuedAt = issuedAt),
            )
            // 다른 사용자의 쿠폰
            userCouponRepositoryPort.save(
                UserCoupon.issue(couponTemplateId = 1L, userId = 8L, issuedAt = issuedAt),
            )

            val found = userCouponRepositoryPort.findAllByUserId(9L)

            assertThat(found).hasSize(2)
            assertThat(found.map { it.id }).containsExactly(second.id, first.id)
            assertThat(found.map { it.userId }).containsOnly(9L)
        }

        @DisplayName("발급 쿠폰이 없으면 빈 목록을 반환한다.")
        @Test
        fun returnsEmpty_whenNoCoupons() {
            assertThat(userCouponRepositoryPort.findAllByUserId(9L)).isEmpty()
        }
    }

    @DisplayName("findById를 호출할 때, 존재하지 않으면 null을 반환한다.")
    @Test
    fun findById_returnsNull_whenMissing() {
        assertThat(userCouponRepositoryPort.findById(9999L)).isNull()
        // JpaRepository 직접 확인으로도 비어 있음을 보장
        assertThat(userCouponJpaRepository.findById(9999L)).isEmpty
    }
}

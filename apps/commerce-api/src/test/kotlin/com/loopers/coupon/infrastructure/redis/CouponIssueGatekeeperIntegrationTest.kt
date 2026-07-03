package com.loopers.coupon.infrastructure.redis

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.error.ConflictException
import com.loopers.utils.RedisCleanUp
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueGatekeeperIntegrationTest @Autowired constructor(
    private val couponIssueGatekeeper: CouponIssueGatekeeper,
    private val couponRepository: CouponRepository,
    private val databaseCleanup: DatabaseCleanup,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
        redisCleanUp.truncateAll()
    }

    @DisplayName("한도만큼만 통과시키고 초과 요청은 CONFLICT(SOLD_OUT)로 거절한다.")
    @Test
    fun passesUpToLimit_andRejectsBeyond() {
        couponIssueGatekeeper.initialize(couponId = 1L, totalQuantity = 3, issuedQuantity = 0, expiredAt = FUTURE)

        (1L..3L).forEach { userId ->
            assertThatCode { couponIssueGatekeeper.tryPass(1L, userId) }.doesNotThrowAnyException()
        }
        val result = assertThrows<ConflictException> { couponIssueGatekeeper.tryPass(1L, 4L) }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.SOLD_OUT)
    }

    @DisplayName("이미 접수한 사용자의 재요청은 CONFLICT(ALREADY_ISSUED)로 거절하고 재고를 추가 차감하지 않는다.")
    @Test
    fun rejectsDuplicateUser_withoutDoubleDecrement() {
        couponIssueGatekeeper.initialize(couponId = 1L, totalQuantity = 2, issuedQuantity = 0, expiredAt = FUTURE)
        couponIssueGatekeeper.tryPass(1L, 1L)

        val result = assertThrows<ConflictException> { couponIssueGatekeeper.tryPass(1L, 1L) }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_ISSUED) },
            // 중복 거절이 재고를 깎지 않았다면 남은 1장은 다른 사용자가 통과할 수 있다
            { assertThatCode { couponIssueGatekeeper.tryPass(1L, 2L) }.doesNotThrowAnyException() },
        )
    }

    @DisplayName("키가 없으면 DB의 잔여 수량으로 lazy 재구성해 게이트를 적용한다.")
    @Test
    fun rebuildsFromDatabase_whenKeyMissing() {
        val coupon = firstComeCoupon(totalQuantity = 1)

        assertThatCode { couponIssueGatekeeper.tryPass(coupon.id, 1L) }.doesNotThrowAnyException()
        val result = assertThrows<ConflictException> { couponIssueGatekeeper.tryPass(coupon.id, 2L) }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.SOLD_OUT)
    }

    @DisplayName("키도 없고 쿠폰도 없으면 fail-open으로 통과시킨다. (최종 방어는 컨슈머의 DB)")
    @Test
    fun failsOpen_whenNeitherKeyNorCouponExists() {
        assertThatCode { couponIssueGatekeeper.tryPass(999_999L, 1L) }.doesNotThrowAnyException()
    }

    private fun firstComeCoupon(totalQuantity: Long): Coupon = couponRepository.save(
        Coupon(
            type = CouponType.FIXED,
            name = "선착순쿠폰",
            value = 1000,
            minOrderAmount = Money(0),
            expiredAt = FUTURE,
            createdBy = 1L,
            totalQuantity = totalQuantity,
        ),
    )

    private companion object {
        private val FUTURE: LocalDateTime = LocalDateTime.now().plusDays(1)
    }
}

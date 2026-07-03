package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.domain.event.Event
import com.loopers.infrastructure.coupon.EventCouponJpaRepository
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository
import com.loopers.infrastructure.event.EventJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class CouponApplicationServiceIntegrationTest @Autowired constructor(
    private val couponApplicationService: CouponApplicationService,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val future = LocalDateTime.of(2026, 12, 31, 23, 59, 59)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun adminCanCreateUpdateListAndDeleteCouponTemplate() {
        val created = couponApplicationService.create(
            CouponCommand.Create(
                name = "신규가입 10% 할인",
                type = CouponType.RATE,
                value = 10,
                minOrderAmount = 10000,
                expiredAt = future,
            ),
        )

        val updated = couponApplicationService.update(
            couponId = created.couponId,
            command = CouponCommand.Update(
                name = "신규가입 3000원 할인",
                type = CouponType.FIXED,
                value = 3000,
                minOrderAmount = null,
                expiredAt = future.plusDays(1),
            ),
        )
        val list = couponApplicationService.getAll(page = 0, size = 20)

        assertAll(
            { assertThat(updated.name).isEqualTo("신규가입 3000원 할인") },
            { assertThat(updated.type).isEqualTo(CouponType.FIXED) },
            { assertThat(list.map { it.couponId }).contains(created.couponId) },
        )

        couponApplicationService.delete(created.couponId)

        val ex = assertThrows<CoreException> {
            couponApplicationService.get(created.couponId)
        }
        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun userCanIssueCouponAndReadMyCoupons() {
        val coupon = couponApplicationService.create(
            CouponCommand.Create("회원 쿠폰", CouponType.FIXED, 3000, null, future),
        )

        val issued = couponApplicationService.issue(userId = 1L, couponId = coupon.couponId)
        val myCoupons = couponApplicationService.getMyCoupons(userId = 1L, now = LocalDateTime.of(2026, 6, 12, 0, 0))

        assertAll(
            { assertThat(issued.userId).isEqualTo(1L) },
            { assertThat(issued.couponId).isEqualTo(coupon.couponId) },
            { assertThat(issued.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            { assertThat(myCoupons).hasSize(1) },
            { assertThat(myCoupons.single().name).isEqualTo("회원 쿠폰") },
        )
    }

    @Test
    fun duplicateIssueReturnsConflict() {
        val coupon = couponApplicationService.create(
            CouponCommand.Create("중복 방지 쿠폰", CouponType.FIXED, 1000, null, future),
        )
        couponApplicationService.issue(userId = 1L, couponId = coupon.couponId)

        val ex = assertThrows<CoreException> {
            couponApplicationService.issue(userId = 1L, couponId = coupon.couponId)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun issueRejectsFirstComeFirstServedCoupon() {
        val event = eventJpaRepository.save(
            Event(
                name = "Summer coupon event",
                startsAt = LocalDateTime.of(2026, 7, 3, 10, 0),
                endsAt = LocalDateTime.of(2026, 7, 3, 18, 0),
            ),
        )
        val coupon = eventCouponJpaRepository.save(
            EventCoupon(
                name = "선착순 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = future,
                eventId = event.id,
                totalQuantity = 10,
            ),
        )

        val ex = assertThrows<CoreException> {
            couponApplicationService.issue(userId = 1L, couponId = coupon.id)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun myCouponsShowsExpiredStatusWhenTemplateExpiredAfterIssue() {
        val coupon = couponApplicationService.create(
            CouponCommand.Create(
                name = "기간 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = LocalDateTime.of(2026, 6, 12, 12, 0),
            ),
        )
        couponApplicationService.issue(userId = 1L, couponId = coupon.couponId, now = LocalDateTime.of(2026, 6, 12, 11, 59))

        val myCoupons = couponApplicationService.getMyCoupons(userId = 1L, now = LocalDateTime.of(2026, 6, 12, 12, 0))

        assertThat(myCoupons.single().status).isEqualTo(IssuedCouponStatus.EXPIRED)
    }

    @Test
    fun useOwnedCouponCalculatesDiscountAndMarksIssueUsedOnce() {
        val coupon = couponApplicationService.create(
            CouponCommand.Create("사용 쿠폰", CouponType.FIXED, 3000, null, future),
        )
        couponApplicationService.issue(userId = 1L, couponId = coupon.couponId)

        val applied = couponApplicationService.useOwnedCoupon(
            userId = 1L,
            couponId = coupon.couponId,
            orderAmount = 12000,
            now = LocalDateTime.of(2026, 6, 12, 0, 0),
        )

        val issue = issuedCouponJpaRepository.findByUserIdAndCouponIdAndDeletedAtIsNull(1L, coupon.couponId)!!
        assertAll(
            { assertThat(applied.discountAmount).isEqualTo(3000) },
            { assertThat(applied.paymentAmount).isEqualTo(9000) },
            { assertThat(issue.status).isEqualTo(IssuedCouponStatus.USED) },
        )

        val ex = assertThrows<CoreException> {
            couponApplicationService.useOwnedCoupon(
                userId = 1L,
                couponId = coupon.couponId,
                orderAmount = 12000,
                now = LocalDateTime.of(2026, 6, 12, 0, 0),
            )
        }
        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun otherUsersCouponCannotBeUsed() {
        val coupon = couponApplicationService.create(
            CouponCommand.Create("소유자 쿠폰", CouponType.FIXED, 1000, null, future),
        )
        couponApplicationService.issue(userId = 2L, couponId = coupon.couponId)

        val ex = assertThrows<CoreException> {
            couponApplicationService.useOwnedCoupon(
                userId = 1L,
                couponId = coupon.couponId,
                orderAmount = 12000,
                now = LocalDateTime.of(2026, 6, 12, 0, 0),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }
}

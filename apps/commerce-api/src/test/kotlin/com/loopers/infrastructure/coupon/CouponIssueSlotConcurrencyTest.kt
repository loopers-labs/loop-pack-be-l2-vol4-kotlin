package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class CouponIssueSlotConcurrencyTest {
    @Autowired lateinit var couponRepository: CouponRepository

    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("총 수량 N개 쿠폰에 M(>N)개 동시 슬롯 확보 요청이 와도 정확히 N개만 성공한다.")
    @Test
    fun claimsExactlyTotalQuantityUnderConcurrency() {
        // arrange
        val total = 100
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순",
                type = CouponType.FIXED,
                discountValue = BigDecimal("1000"),
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(1),
                totalQuantity = total,
            ),
        )

        // act
        val successes = java.util.concurrent.atomic.AtomicInteger(0)
        runConcurrently(threadCount = 300) {
            if (couponRepository.claimIssueSlot(coupon.id)) successes.incrementAndGet()
        }

        // assert
        assertThat(successes.get()).isEqualTo(total)
        assertThat(couponRepository.findActiveById(coupon.id)?.issuedCount).isEqualTo(total)
    }
}

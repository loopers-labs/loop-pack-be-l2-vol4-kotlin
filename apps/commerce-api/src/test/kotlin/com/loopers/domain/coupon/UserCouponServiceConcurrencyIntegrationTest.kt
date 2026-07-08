package com.loopers.domain.coupon

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class UserCouponServiceConcurrencyIntegrationTest @Autowired constructor(
    private val userCouponService: UserCouponService,
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
    private val couponTemplateRepositoryPort: CouponTemplateRepositoryPort,
    private val transactionManager: PlatformTransactionManager,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 발급 쿠폰으로 여러 스레드가 동시에 주문에 사용해도, 쿠폰은 정확히 1번만 사용된다.")
    @Test
    fun usesExactlyOnce_underConcurrency() {
        // setup: 템플릿 + AVAILABLE 발급 쿠폰 1장 (테스트 메서드에 @Transactional 없음 → 즉시 커밋되어 워커 스레드가 본다)
        val userId = 7L
        val now = LocalDateTime.parse("2026-06-09T10:00:00")
        val template = couponTemplateRepositoryPort.save(
            CouponTemplate.create(
                name = "동시성 테스트 쿠폰",
                type = CouponType.FIXED,
                value = 1_000L,
                minOrderAmount = 0L,
                expiredAt = now.plusDays(30),
                totalCount = 100L,
            ),
        )
        val coupon = userCouponRepositoryPort.save(
            UserCoupon.issue(couponTemplateId = template.id, userId = userId, issuedAt = now),
        )

        // 워커별 트랜잭션 경계. 프로덕션의 OrderPlacement.place(@Transactional) 경계를 시뮬레이션해
        // read~use~save 가 한 트랜잭션에 묶이도록 한다. (도메인 서비스/테스트 메서드에는 @Transactional 금지 규칙)
        val txTemplate = TransactionTemplate(transactionManager)

        val threadCount = 10
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        repeat(threadCount) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    txTemplate.execute {
                        userCouponService.useForOrder(
                            couponId = coupon.id,
                            userId = userId,
                            orderAmount = 10_000L,
                            now = now,
                        )
                    }
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown() // 동시 출발
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 정확히 1건만 성공, 나머지는 실패 (낙관적 락 충돌 또는 이미 USED 검증으로)
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(threadCount - 1)

        // 쿠폰 최종 상태는 USED 1회, usedAt 1회
        val finalCoupon = userCouponRepositoryPort.findById(coupon.id)
        assertThat(finalCoupon).isNotNull
        assertThat(finalCoupon?.status).isEqualTo(CouponStatus.USED)
        assertThat(finalCoupon?.usedAt).isNotNull
    }
}

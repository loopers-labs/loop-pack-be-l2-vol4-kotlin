package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.RequestCouponIssueUsecase
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class RequestCouponIssueUsecaseIntegrationTest @Autowired constructor(
    private val requestUsecase: RequestCouponIssueUsecase,
    private val couponRepository: CouponRepository,
    private val requestRepository: CouponIssueRequestRepository,
    private val outboxRepository: OutboxEventRepository,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("발급 요청을 접수하면 PENDING 요청과 coupon-issue-requests outbox 행이 같은 tx로 생성된다.")
    @Test
    fun acceptsRequestAndEnqueuesOutbox() {
        // arrange: 활성 유저 + 선착순 쿠폰
        val loginId = "loopers01"
        val password = "Pass1234!"
        userService.signUp(
            UserService.SignUpCommand(
                loginId = loginId,
                password = password,
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@loopers.com",
            ),
        )
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순",
                type = CouponType.FIXED,
                discountValue = BigDecimal("1000"),
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(1),
                totalQuantity = 100,
            ),
        )

        // act
        val requestId = requestUsecase.execute(loginId, password, coupon.id)

        // assert
        val req = requestRepository.findByRequestId(requestId)
        assertThat(req?.status).isEqualTo(CouponIssueStatus.PENDING)
        val pending = outboxRepository.findTopPending(10)
        assertThat(pending).anyMatch { it.topic == KafkaTopics.COUPON_ISSUE_REQUESTS && it.partitionKey == coupon.id.toString() }
    }
}

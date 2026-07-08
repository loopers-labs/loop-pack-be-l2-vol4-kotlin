package com.loopers.interfaces.consumer

import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.application.user.UserFacade
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponIssuePipelineIntegrationTest @Autowired constructor(
    private val couponIssueFacade: CouponIssueFacade,
    private val userFacade: UserFacade,
    private val couponService: CouponService,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val rawPassword = "Valid1!pw"

    companion object {
        private val GROUP = "coupon-test-${UUID.randomUUID()}"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { KafkaTestContainer.bootstrapServers }
            registry.add("coupon.consumer.enabled") { "true" }
            registry.add("coupon.consumer.group") { GROUP }
            registry.add("spring.kafka.properties.auto.offset.reset") { "earliest" }
        }
    }

    @BeforeAll
    fun createTopic() {
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainer.bootstrapServers),
        ).use { admin ->
            try {
                admin.createTopics(listOf(NewTopic("coupon-issue-requests", 3, 1.toShort()))).all().get()
            } catch (e: ExecutionException) {
                if (e.cause !is TopicExistsException) throw e
            }
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("발급 요청 API 는 requestId 를 즉시 반환하고, Consumer 가 실제 발급하면 결과가 SUCCESS 로 확정된다.")
    @Test
    fun requestIssue_thenConsumerIssues_resultSuccess() {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val coupon = couponService.register("선착순", DiscountType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), issuableQuantity = 100)

        // act: API 는 발행만 하고 requestId 즉시 반환
        val requestId = couponIssueFacade.requestIssue("user0", rawPassword, coupon.id)

        // assert: Consumer 가 순차 발급 → 결과 SUCCESS
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(couponIssueFacade.getResult(requestId).status).isEqualTo(CouponIssueStatus.SUCCESS)
        }
        assertThat(userCouponRepository.existsByUserIdAndCouponId(user.id, coupon.id)).isTrue()
    }
}

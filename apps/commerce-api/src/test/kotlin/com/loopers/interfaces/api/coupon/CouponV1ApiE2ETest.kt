package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.enums.CouponIssueDisplayStatus
import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.enums.CouponIssueStatus
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.user.PasswordEncoder
import com.loopers.infrastructure.coupon.entity.CouponEntity
import com.loopers.infrastructure.coupon.entity.CouponIssueEntity
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity
import com.loopers.infrastructure.coupon.repository.CouponIssueRequestJpaRepository
import com.loopers.infrastructure.coupon.repository.CouponIssueJpaRepository
import com.loopers.infrastructure.coupon.repository.CouponJpaRepository
import com.loopers.infrastructure.member.entity.MemberEntity
import com.loopers.infrastructure.member.repository.MemberJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.coupon.dto.CouponV1Dto
import com.loopers.interfaces.api.user.dto.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val COUPON_ISSUE_REQUEST_TOPIC = "coupon-issue-requests-api-test"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "commerce.events.coupon-issue-request-topic=$COUPON_ISSUE_REQUEST_TOPIC",
    ],
)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setUp() {
        createTopicIfAbsent(COUPON_ISSUE_REQUEST_TOPIC)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    inner class IssueCoupon {
        @DisplayName("로그인한 회원의 쿠폰 발급 요청을 접수한다")
        @Test
        fun issuesCoupon() {
            val member = createMember()
            val coupon = couponJpaRepository.save(
                createCouponEntity(
                    type = DiscountType.FIXED,
                    discountValue = 3_000L,
                    minOrderAmount = 10_000L,
                    expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
                ),
            )

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>>() {},
            )

            val issues = couponIssueJpaRepository.findAll()
            val requests = couponIssueRequestJpaRepository.findAll()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
                { assertThat(response.body?.data?.memberId).isEqualTo(member.id) },
                { assertThat(response.body?.data?.status).isEqualTo(CouponIssueRequestStatus.REQUESTED) },
                { assertThat(response.body?.data?.issueId).isNull() },
                { assertThat(issues).isEmpty() },
                { assertThat(requests).hasSize(1) },
                { assertThat(requests.single().requestId).isEqualTo(response.body?.data?.requestId) },
            )
        }

        @DisplayName("삭제된 쿠폰 템플릿도 발급 요청은 접수한다")
        @Test
        fun acceptsRequest_whenCouponIsDeleted() {
            createMember()
            val coupon = couponJpaRepository.save(createCouponEntity(isDeleted = true))

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(CouponIssueRequestStatus.REQUESTED) },
                { assertThat(couponIssueJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("만료된 쿠폰 템플릿도 발급 요청은 접수한다")
        @Test
        fun acceptsRequest_whenCouponIsExpired() {
            createMember()
            val coupon = couponJpaRepository.save(
                createCouponEntity(expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00")),
            )

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(CouponIssueRequestStatus.REQUESTED) },
                { assertThat(couponIssueJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("인증 정보가 올바르지 않으면 쿠폰을 발급할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val coupon = couponJpaRepository.save(createCouponEntity())

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(couponIssueJpaRepository.findAll()).isEmpty() },
            )
        }
    }

    @DisplayName("GET /api/v1/coupons/issue-requests/{requestId}")
    @Nested
    inner class GetIssueRequest {
        @DisplayName("로그인한 회원의 쿠폰 발급 요청 상태를 조회한다")
        @Test
        fun returnsIssueRequest() {
            val member = createMember()
            val coupon = couponJpaRepository.save(createCouponEntity())
            val request = couponIssueRequestJpaRepository.save(
                CouponIssueRequestEntity(
                    requestId = "request-1",
                    couponId = coupon.id,
                    memberId = member.id,
                    status = CouponIssueRequestStatus.REQUESTED,
                    issueId = null,
                    reason = null,
                    requestedAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
                ),
            )

            val response = testRestTemplate.exchange(
                "$COUPON_ISSUE_REQUESTS_ENDPOINT/${request.requestId}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.requestId).isEqualTo(request.requestId) },
                { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
                { assertThat(response.body?.data?.memberId).isEqualTo(member.id) },
                { assertThat(response.body?.data?.status).isEqualTo(CouponIssueRequestStatus.REQUESTED) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    inner class GetMyCoupons {
        @DisplayName("로그인한 회원의 쿠폰 목록을 상태와 함께 조회한다")
        @Test
        fun returnsMyCoupons() {
            val member = createMember()
            val otherMember = createMember(loginId = "other123")
            val coupon = couponJpaRepository.save(createCouponEntity())
            val availableIssue = couponIssueJpaRepository.save(
                createCouponIssueEntity(
                    coupon = coupon,
                    memberId = member.id,
                    expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
                ),
            )
            val expiredIssue = couponIssueJpaRepository.save(
                createCouponIssueEntity(
                    coupon = coupon,
                    memberId = member.id,
                    expiredAt = ZonedDateTime.parse("2000-01-01T00:00:00+09:00"),
                ),
            )
            val usedIssue = couponIssueJpaRepository.save(
                createCouponIssueEntity(
                    coupon = coupon,
                    memberId = member.id,
                    status = CouponIssueStatus.USED,
                    expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
                    usedAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
                ),
            )
            couponIssueJpaRepository.save(createCouponIssueEntity(coupon = coupon, memberId = otherMember.id))

            val response = testRestTemplate.exchange(
                MY_COUPONS_ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<List<UserV1Dto.CouponIssueResponse>>>() {},
            )

            val issues = response.body?.data.orEmpty()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(issues).hasSize(3) },
                {
                    assertThat(issues)
                        .extracting<Long> { it.issueId }
                        .containsExactlyInAnyOrder(availableIssue.id, expiredIssue.id, usedIssue.id)
                },
                {
                    assertThat(issues.associate { it.issueId to it.status })
                        .containsEntry(availableIssue.id, CouponIssueDisplayStatus.AVAILABLE)
                        .containsEntry(expiredIssue.id, CouponIssueDisplayStatus.EXPIRED)
                        .containsEntry(usedIssue.id, CouponIssueDisplayStatus.USED)
                },
            )
        }

        @DisplayName("인증 정보가 올바르지 않으면 내 쿠폰 목록을 조회할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()

            val response = testRestTemplate.exchange(
                MY_COUPONS_ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<List<UserV1Dto.CouponIssueResponse>>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun createMember(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): MemberEntity {
        return memberJpaRepository.save(
            MemberEntity(
                loginId = loginId,
                password = PasswordEncoder.encode(password),
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun createCouponEntity(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
        isDeleted: Boolean = false,
    ): CouponEntity {
        return CouponEntity(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            isDeleted = isDeleted,
        )
    }

    private fun createCouponIssueEntity(
        coupon: CouponEntity,
        memberId: Long,
        status: CouponIssueStatus = CouponIssueStatus.AVAILABLE,
        expiredAt: ZonedDateTime = coupon.expiredAt,
        usedAt: ZonedDateTime? = null,
    ): CouponIssueEntity {
        return CouponIssueEntity(
            memberId = memberId,
            couponId = coupon.id,
            status = status,
            type = coupon.type,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = expiredAt,
            usedAt = usedAt,
        )
    }

    private fun createAuthHeaders(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-LoginId", loginId)
            set("X-Loopers-LoginPw", password)
        }
    }

    private companion object {
        private const val COUPONS_ENDPOINT = "/api/v1/coupons"
        private const val COUPON_ISSUE_REQUESTS_ENDPOINT = "/api/v1/coupons/issue-requests"
        private const val MY_COUPONS_ENDPOINT = "/api/v1/users/me/coupons"
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"

        private fun createTopicIfAbsent(topic: String) {
            val properties = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:19092")
            AdminClient.create(properties).use { adminClient ->
                try {
                    adminClient.createTopics(listOf(NewTopic(topic, 1, 1)))
                        .all()
                        .get(5, TimeUnit.SECONDS)
                } catch (e: ExecutionException) {
                    if (e.cause !is TopicExistsException) {
                        throw e
                    }
                }
            }
        }
    }
}

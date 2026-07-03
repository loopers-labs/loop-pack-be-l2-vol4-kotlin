package com.loopers.interfaces.api.event

import com.loopers.application.event.EventCouponStatus
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.event.Event
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.coupon.CouponPublishOutboxJpaRepository
import com.loopers.infrastructure.coupon.EventCouponJpaRepository
import com.loopers.infrastructure.event.EventJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["spring.kafka.listener.auto-startup=false"])
class EventCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val outboxJpaRepository: CouponPublishOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun getEventCouponReturnsAvailableStatusForAuthenticatedUser() {
        saveUser()
        val coupon = saveEventCoupon(totalQuantity = 3)
        val responseType = object : ParameterizedTypeReference<ApiResponse<EventCouponV1Dto.DetailResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/events/coupon/${coupon.id}",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
            { assertThat(response.body?.data?.eventName).isEqualTo("Summer coupon event") },
            { assertThat(response.body?.data?.status).isEqualTo(EventCouponStatus.AVAILABLE) },
        )
    }

    @Test
    fun requestReturnsAcceptedAndOutboxWhenAvailable() {
        saveUser()
        val coupon = saveEventCoupon(totalQuantity = 3)
        val responseType = object : ParameterizedTypeReference<ApiResponse<EventCouponV1Dto.RequestResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/events/coupon/${coupon.id}",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED) },
            { assertThat(response.body?.data?.status).isEqualTo(EventCouponStatus.REQUESTED) },
            { assertThat(response.body?.data?.idempotencyKey).isNotBlank() },
            { assertThat(outboxJpaRepository.count()).isEqualTo(1) },
        )
    }

    @Test
    fun duplicateRequestReturnsOkAlreadyRegistered() {
        saveUser()
        val coupon = saveEventCoupon(totalQuantity = 3)
        val responseType = object : ParameterizedTypeReference<ApiResponse<EventCouponV1Dto.RequestResponse>>() {}
        testRestTemplate.exchange(
            "/api/v1/events/coupon/${coupon.id}",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        val response = testRestTemplate.exchange(
            "/api/v1/events/coupon/${coupon.id}",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.status).isEqualTo(EventCouponStatus.ALREADY_REGISTERED) },
            { assertThat(response.body?.data?.idempotencyKey).isNull() },
            { assertThat(outboxJpaRepository.count()).isEqualTo(1) },
        )
    }

    @Test
    fun requestRequiresAuthentication() {
        val coupon = saveEventCoupon(totalQuantity = 3)
        val responseType = object : ParameterizedTypeReference<ApiResponse<EventCouponV1Dto.RequestResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/events/coupon/${coupon.id}",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders()),
            responseType,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun saveEventCoupon(totalQuantity: Long): EventCoupon {
        val event = eventJpaRepository.save(
            Event(
                name = "Summer coupon event",
                startsAt = LocalDateTime.now().minusHours(1),
                endsAt = LocalDateTime.now().plusHours(1),
            ),
        )
        return eventCouponJpaRepository.save(
            EventCoupon(
                name = "선착순 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = LocalDateTime.now().plusDays(30),
                eventId = event.id,
                totalQuantity = totalQuantity,
            ),
        )
    }

    private fun saveUser(role: UserRole = UserRole.CONSUMER): User =
        userJpaRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
                role = role,
            ),
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", "loopers01")
        add("X-Loopers-LoginPw", "abcd1234")
    }
}

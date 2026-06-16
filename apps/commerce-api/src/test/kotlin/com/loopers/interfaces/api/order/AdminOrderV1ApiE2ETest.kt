package com.loopers.interfaces.api.order

import com.loopers.domain.order.OrderStatus
import com.loopers.infrastructure.order.OrderEntity
import com.loopers.infrastructure.order.OrderItemEntity
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminOrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    inner class GetOrders {
        @DisplayName("관리자가 전체 주문 목록을 페이징 조회한다")
        @Test
        fun getsOrders() {
            val now = ZonedDateTime.now()
            val firstOrder = createOrder(memberId = 1L, orderedAt = now.minusDays(1), totalAmount = 10_000L)
            val secondOrder = createOrder(memberId = 2L, orderedAt = now, totalAmount = 20_000L)

            val response = testRestTemplate.exchange(
                "$ORDERS_ENDPOINT?page=0&size=20",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminOrderV1Dto.OrderSummaryResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.data?.map { it.orderId }).containsExactly(secondOrder.id, firstOrder.id) },
                { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(2L) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    inner class GetOrder {
        @DisplayName("관리자가 주문 상세를 조회한다")
        @Test
        fun getsOrder() {
            val order = createOrder(memberId = 1L)

            val response = testRestTemplate.exchange(
                "$ORDERS_ENDPOINT/${order.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminOrderV1Dto.OrderDetailResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.orderId).isEqualTo(order.id) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.single()?.brandName).isEqualTo("loopers") },
            )
        }

        @DisplayName("존재하지 않는 주문은 조회할 수 없다")
        @Test
        fun returnsNotFound_whenOrderDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$ORDERS_ENDPOINT/999",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminOrderV1Dto.OrderDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun createOrder(
        memberId: Long,
        orderedAt: ZonedDateTime = ZonedDateTime.now(),
        totalAmount: Long = 10_000L,
    ): OrderEntity {
        val order = OrderEntity(
            orderNumber = "admin-order-$memberId-${orderedAt.toInstant().toEpochMilli()}-$totalAmount",
            memberId = memberId,
            status = OrderStatus.COMPLETED,
            totalAmount = totalAmount,
            orderedAt = orderedAt,
        )
        order.addItem(
            OrderItemEntity(
                productId = 1L,
                productName = "hoodie",
                brandName = "loopers",
                unitPrice = totalAmount,
                quantity = 1L,
                totalAmount = totalAmount,
            ),
        )

        return orderJpaRepository.save(order)
    }

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "loopers.admin")
        }
    }

    private companion object {
        private const val ORDERS_ENDPOINT = "/api-admin/v1/orders"
    }
}

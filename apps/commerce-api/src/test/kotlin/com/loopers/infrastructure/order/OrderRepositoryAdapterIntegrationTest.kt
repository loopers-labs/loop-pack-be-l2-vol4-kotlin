package com.loopers.infrastructure.order

import com.loopers.domain.common.PageRequest
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItems
import com.loopers.domain.order.OrderRepositoryPort
import com.loopers.domain.order.OrderStatus
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class OrderRepositoryAdapterIntegrationTest @Autowired constructor(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun item(
        productId: Long = 1L,
        quantity: Int = 1,
        snapshotPrice: Long = 1_000L,
        name: String = "p$productId",
        brand: String = "Nike",
    ): OrderItem = OrderItem(
        productId = productId,
        quantity = quantity,
        snapshotProductName = name,
        snapshotPrice = snapshotPrice,
        snapshotBrandName = brand,
    )

    private fun saveOrder(
        userId: Long = 1L,
        items: List<OrderItem> = listOf(item()),
        orderedAt: ZonedDateTime = ZonedDateTime.parse("2026-05-01T10:00:00+09:00[Asia/Seoul]"),
        status: OrderStatus = OrderStatus.CREATED,
    ): Order {
        val base = Order.create(userId = userId, items = OrderItems(items), orderedAt = orderedAt)
        val initial = orderRepositoryPort.save(base)
        return if (status == OrderStatus.CREATED) {
            initial
        } else {
            orderRepositoryPort.save(initial.copy(status = status))
        }
    }

    @DisplayName("save")
    @Nested
    inner class Save {
        @DisplayName("id=0 인 Order를 저장하면 id가 부여되고 OrderItem 들도 함께 INSERT 된다.")
        @Test
        fun insertsOrderAndItems() {
            val items = listOf(item(productId = 1L, quantity = 2, snapshotPrice = 1_000L), item(productId = 2L, quantity = 1, snapshotPrice = 3_000L))
            val saved = saveOrder(userId = 7L, items = items)

            val reloaded = orderRepositoryPort.findById(saved.id)

            assertThat(saved.id).isGreaterThan(0L)
            assertThat(reloaded).isNotNull
            assertThat(reloaded?.userId).isEqualTo(7L)
            assertThat(reloaded?.status).isEqualTo(OrderStatus.CREATED)
            assertThat(reloaded?.totalAmount).isEqualTo(5_000L)
            assertThat(reloaded?.items?.size).isEqualTo(2)
            assertThat(reloaded?.items?.productIds()).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("id가 부여된 Order를 다시 저장하면 status가 UPDATE 되고 OrderItem 은 그대로 유지된다.")
        @Test
        fun updatesStatusOnly() {
            val saved = saveOrder(items = listOf(item(productId = 1L, quantity = 2, snapshotPrice = 500L)))

            val transitioned = orderRepositoryPort.save(saved.updateStatus(OrderStatus.PAYMENT_PENDING))

            assertThat(transitioned.id).isEqualTo(saved.id)
            assertThat(transitioned.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
            assertThat(transitioned.items.size).isEqualTo(1)
            assertThat(transitioned.items.list.first().snapshotPrice).isEqualTo(500L)
        }
    }

    @DisplayName("findById")
    @Nested
    inner class FindById {
        @DisplayName("존재하지 않는 id 면 null 을 반환한다.")
        @Test
        fun returnsNull_whenMissing() {
            assertThat(orderRepositoryPort.findById(9999L)).isNull()
        }
    }

    @DisplayName("findAllByUserId")
    @Nested
    inner class FindAllByUserId {
        @DisplayName("startAt~endAt 기간 내 본인 주문만 반환한다.")
        @Test
        fun filtersByPeriodAndUser() {
            val me = 1L
            val other = 2L
            saveOrder(userId = me, orderedAt = ZonedDateTime.parse("2026-04-15T10:00:00+09:00[Asia/Seoul]"))
            saveOrder(userId = me, orderedAt = ZonedDateTime.parse("2026-05-05T10:00:00+09:00[Asia/Seoul]"))
            saveOrder(userId = me, orderedAt = ZonedDateTime.parse("2026-05-10T10:00:00+09:00[Asia/Seoul]"))
            saveOrder(userId = other, orderedAt = ZonedDateTime.parse("2026-05-07T10:00:00+09:00[Asia/Seoul]"))

            val page = orderRepositoryPort.findAllByUserId(
                userId = me,
                startAt = ZonedDateTime.parse("2026-05-01T00:00:00+09:00[Asia/Seoul]"),
                endAt = ZonedDateTime.parse("2026-05-31T23:59:59+09:00[Asia/Seoul]"),
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(page.totalElements).isEqualTo(2L)
            assertThat(page.items).hasSize(2)
            assertThat(page.items.map { it.userId }.distinct()).containsExactly(me)
        }

        @DisplayName("기간 내 주문이 없으면 빈 페이지를 반환한다.")
        @Test
        fun returnsEmpty_whenNoneInPeriod() {
            saveOrder(userId = 1L, orderedAt = ZonedDateTime.parse("2026-04-01T10:00:00+09:00[Asia/Seoul]"))

            val page = orderRepositoryPort.findAllByUserId(
                userId = 1L,
                startAt = ZonedDateTime.parse("2026-05-01T00:00:00+09:00[Asia/Seoul]"),
                endAt = ZonedDateTime.parse("2026-05-31T23:59:59+09:00[Asia/Seoul]"),
                pageRequest = PageRequest(page = 0, size = 10),
            )

            assertThat(page.totalElements).isEqualTo(0L)
            assertThat(page.items).isEmpty()
        }
    }

    @DisplayName("findAll (어드민)")
    @Nested
    inner class FindAll {
        @DisplayName("페이지네이션이 동작한다.")
        @Test
        fun paginatesAcrossAllOrders() {
            repeat(5) { idx ->
                saveOrder(
                    userId = (idx + 1).toLong(),
                    orderedAt = ZonedDateTime.parse("2026-05-${10 + idx}T10:00:00+09:00[Asia/Seoul]"),
                )
            }

            val first = orderRepositoryPort.findAll(PageRequest(page = 0, size = 3))
            val second = orderRepositoryPort.findAll(PageRequest(page = 1, size = 3))

            assertThat(first.totalElements).isEqualTo(5L)
            assertThat(first.items).hasSize(3)
            assertThat(second.items).hasSize(2)
        }
    }

    @DisplayName("existsByProductIdInAndStatusIn")
    @Nested
    inner class ExistsCheck {
        @DisplayName("productId 가 미완료 주문 상태(PAYMENT_PENDING 등)에 포함되면 true 를 반환한다.")
        @Test
        fun returnsTrue_whenIncompleteOrderContainsProduct() {
            saveOrder(items = listOf(item(productId = 100L)), status = OrderStatus.PAYMENT_PENDING)

            val exists = orderRepositoryPort.existsByProductIdInAndStatusIn(
                productIds = listOf(100L),
                statuses = OrderStatus.INCOMPLETE_STATUSES,
            )

            assertThat(exists).isTrue()
        }

        @DisplayName("해당 productId 의 주문이 모두 CANCELLED/DELIVERED 면 false 를 반환한다.")
        @Test
        fun returnsFalse_whenAllCompleted() {
            saveOrder(items = listOf(item(productId = 200L)), status = OrderStatus.CANCELLED)
            // DELIVERED 도 같이 보강 — PP -> PC -> S -> D 전이가 필요해서 직접 copy로 만들어 저장
            val base = saveOrder(items = listOf(item(productId = 200L)), status = OrderStatus.PAYMENT_PENDING)
            val completed = orderRepositoryPort.save(base.updateStatus(OrderStatus.PAYMENT_COMPLETED))
            val shipping = orderRepositoryPort.save(completed.updateStatus(OrderStatus.SHIPPING))
            orderRepositoryPort.save(shipping.updateStatus(OrderStatus.DELIVERED))

            val exists = orderRepositoryPort.existsByProductIdInAndStatusIn(
                productIds = listOf(200L),
                statuses = OrderStatus.INCOMPLETE_STATUSES,
            )

            assertThat(exists).isFalse()
        }

        @DisplayName("productIds 가 비어 있으면 항상 false 를 반환한다.")
        @Test
        fun returnsFalse_whenProductIdsEmpty() {
            saveOrder(items = listOf(item(productId = 100L)), status = OrderStatus.PAYMENT_PENDING)

            val exists = orderRepositoryPort.existsByProductIdInAndStatusIn(
                productIds = emptyList(),
                statuses = OrderStatus.INCOMPLETE_STATUSES,
            )

            assertThat(exists).isFalse()
        }

        @DisplayName("해당 productId 의 주문이 아예 없으면 false 를 반환한다.")
        @Test
        fun returnsFalse_whenNoOrders() {
            saveOrder(items = listOf(item(productId = 100L)), status = OrderStatus.PAYMENT_PENDING)

            val exists = orderRepositoryPort.existsByProductIdInAndStatusIn(
                productIds = listOf(999L),
                statuses = OrderStatus.INCOMPLETE_STATUSES,
            )

            assertThat(exists).isFalse()
        }
    }
}

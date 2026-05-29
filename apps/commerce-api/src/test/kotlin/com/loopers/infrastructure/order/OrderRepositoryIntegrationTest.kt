package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItemSnapshot
import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDateTime

@DataJpaTest
class OrderRepositoryIntegrationTest @Autowired constructor(
    private val orderJpaRepository: OrderJpaRepository,
) {
    private val repository = OrderRepositoryImpl(orderJpaRepository)

    private fun order(userId: Long = 1L) = Order.create(
        userId = userId,
        snapshots = listOf(
            OrderItemSnapshot(1L, 10L, "에어맥스", "나이키", Money(1000), 2),
            OrderItemSnapshot(2L, 10L, "줌플라이", "나이키", Money(500), 1),
        ),
    )

    @DisplayName("주문을 저장하면, 주문 항목이 cascade로 함께 저장된다.")
    @Test
    fun persistsOrderItems_byCascade() {
        val saved = repository.save(order())

        val found = repository.findById(saved.id)

        assertAll(
            { assertThat(found).isNotNull() },
            { assertThat(found!!.items).hasSize(2) },
            { assertThat(found!!.totalAmount).isEqualTo(Money(2500)) },
        )
    }

    @DisplayName("본인 주문을 주문 시각 범위로 조회하고, 범위 밖·타 사용자 주문은 제외한다.")
    @Test
    fun findsByUserIdAndOrderedAtBetween() {
        repository.save(order(userId = 1L))
        repository.save(order(userId = 2L))
        val now = LocalDateTime.now()

        assertAll(
            { assertThat(repository.findByUserIdAndOrderedAtBetween(1L, now.minusDays(1), now.plusDays(1))).hasSize(1) },
            { assertThat(repository.findByUserIdAndOrderedAtBetween(1L, now.minusDays(10), now.minusDays(9))).isEmpty() },
            { assertThat(repository.findByUserIdAndOrderedAtBetween(2L, now.minusDays(1), now.plusDays(1))).hasSize(1) },
        )
    }
}

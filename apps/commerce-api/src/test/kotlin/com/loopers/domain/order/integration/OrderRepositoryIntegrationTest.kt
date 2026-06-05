package com.loopers.domain.order.integration

import com.loopers.domain.order.infrastructure.persistence.OrderItemJpaRepository
import com.loopers.domain.order.port.OrderRepository
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_도메인_생성
import com.loopers.domain.order.support.OrderSteps.Companion.주문_도메인_생성
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
class OrderRepositoryIntegrationTest
    @Autowired
    constructor(
        private val orderRepository: OrderRepository,
        private val orderItemJpaRepository: OrderItemJpaRepository,
        private val entityManagerFactory: EntityManagerFactory,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `주문과_주문항목을_저장한다`() {
            val order = 주문_도메인_생성(
                id = 0L,
                items = listOf(
                    주문항목_도메인_생성(productId = 10L, quantity = 2, unitPrice = 10_000),
                    주문항목_도메인_생성(productId = 20L, quantity = 1, unitPrice = 5_000),
                ),
            )

            val saved = orderRepository.save(order)

            assertThat(saved.id).isPositive()
            assertThat(saved.paymentPrice.value).isEqualTo(25_000)
            assertThat(saved.items).hasSize(2)
            assertThat(saved.items).allMatch { it.orderId == saved.id }
            assertThat(orderItemJpaRepository.findByOrderItemIdOrderId(saved.id)).hasSize(2)
        }

        @Test
        fun `주문항목_저장은_기존여부_조회를_항목수만큼_수행하지_않는다`() {
            val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            val order = 주문_도메인_생성(
                id = 0L,
                items = (1L..10L).map { productId ->
                    주문항목_도메인_생성(productId = productId, quantity = 1, unitPrice = 1_000)
                },
            )

            statistics.clear()
            orderRepository.save(order)

            assertThat(statistics.entityLoadCount).isZero()
            assertThat(statistics.entityInsertCount).isEqualTo(11L)
        }
    }

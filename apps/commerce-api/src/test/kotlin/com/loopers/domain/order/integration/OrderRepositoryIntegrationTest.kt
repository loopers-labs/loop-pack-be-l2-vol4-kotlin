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

        @Test
        fun `사용자_주문_목록_조회는_주문항목을_주문수만큼_조회하지_않는다`() {
            val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            repeat(3) { index ->
                orderRepository.save(
                    주문_도메인_생성(
                        id = 0L,
                        orderedUserId = 1L,
                        items = listOf(
                            주문항목_도메인_생성(productId = index * 10L + 1L, quantity = 1, unitPrice = 1_000),
                            주문항목_도메인_생성(productId = index * 10L + 2L, quantity = 1, unitPrice = 2_000),
                        ),
                    ),
                )
            }

            statistics.clear()
            val orders = orderRepository.findByOrderedUserId(
                orderedUserId = 1L,
                startAt = null,
                endAt = null,
            )

            assertThat(orders).hasSize(3)
            assertThat(orders).allSatisfy { order ->
                assertThat(order.items).hasSize(2)
            }
            assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(2L)
        }

        @Test
        fun `관리자_주문_목록_조회는_주문항목을_주문수만큼_조회하지_않는다`() {
            val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            repeat(3) { index ->
                orderRepository.save(
                    주문_도메인_생성(
                        id = 0L,
                        orderedUserId = index + 1L,
                        items = listOf(
                            주문항목_도메인_생성(productId = index * 10L + 1L, quantity = 1, unitPrice = 1_000),
                            주문항목_도메인_생성(productId = index * 10L + 2L, quantity = 1, unitPrice = 2_000),
                        ),
                    ),
                )
            }

            statistics.clear()
            val orders = orderRepository.findAll(page = 0, size = 20)

            assertThat(orders).hasSize(3)
            assertThat(orders).allSatisfy { order ->
                assertThat(order.items).hasSize(2)
            }
            assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(2L)
        }
    }

package com.loopers.order.application

import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.outbox.domain.OutboxStatus
import com.loopers.outbox.infrastructure.OutboxEventJpaRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// outbox 적재는 본 트랜잭션의 실제 커밋/롤백 검증이 핵심 — 클래스 @Transactional 금지(BEFORE_COMMIT 리스너가 침묵함)
@SpringBootTest
@ActiveProfiles("test")
class OrderOutboxIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("주문이 생성되면, 같은 트랜잭션에서 outbox_event 에 OrderCreatedEvent 가 INIT 상태로 적재된다.")
    @Test
    fun insertsOutboxEvent_whenOrderPlaced() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(product.id, 100))

        val info = orderFacade.place(
            command = OrderCreateCommand(
                userId = 1L,
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2, price = 10_000)),
                expectedOriginalAmount = 20_000,
                expectedDiscountAmount = 0,
            ),
        )

        val outboxEvents = outboxEventJpaRepository.findAll()
        assertAll(
            { assertThat(outboxEvents).hasSize(1) },
            { assertThat(outboxEvents[0].aggregateType).isEqualTo("ORDER") },
            { assertThat(outboxEvents[0].aggregateId).isEqualTo(info.id) },
            { assertThat(outboxEvents[0].eventType).isEqualTo("OrderCreatedEvent") },
            { assertThat(outboxEvents[0].status).isEqualTo(OutboxStatus.INIT) },
            { assertThat(outboxEvents[0].payload).contains("\"eventId\"") },
            { assertThat(outboxEvents[0].payload).contains("\"orderId\":${info.id}") },
        )
    }
}

package com.loopers.order.application

import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.runConcurrently
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@ActiveProfiles("test")
class InventoryLockHoldMeasurementTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val transactionManager: PlatformTransactionManager,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("단일 주문의 전체 소요 시간과 락 점유 구간(FOR UPDATE~커밋)을 측정한다.")
    @Test
    fun measuresLockHoldSegment() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("측정용"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(product.id, 10_000_000))
        val command = OrderCreateCommand(
            userId = 1L,
            items = listOf(OrderLineCommand(productId = product.id, quantity = 1, price = 10_000)),
            expectedOriginalAmount = 10_000,
            expectedDiscountAmount = 0,
        )
        val lockSegment = TransactionTemplate(transactionManager)
        val snapshot = OrderItemSnapshot(product.id, 1L, "측정용", null, Money(10_000), 1)

        repeat(100) { orderFacade.place(command) }
        repeat(100) { runLockSegment(lockSegment, product.id, snapshot) }

        val totalNanos = LongArray(300) { measureNanos { orderFacade.place(command) } }
        val lockNanos = LongArray(300) { measureNanos { runLockSegment(lockSegment, product.id, snapshot) } }

        printStats("place() 전체 (①~⑦+커밋)", totalNanos)
        printStats("락 점유 구간 (⑥ FOR UPDATE → ⑦ 저장 → 커밋)", lockNanos)
    }

    @DisplayName("같은 상품 동시 주문(직렬화)과 서로 다른 상품 동시 주문(경합 없음)의 벽시계 시간을 비교한다.")
    @Test
    fun measuresContentionImpact() {
        val sameProduct = productRepository.save(Product(brandId = 1L, name = ProductName("경합상품"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(sameProduct.id, 10_000_000))
        val distinctProducts = (1..10).map {
            val p = productRepository.save(Product(brandId = 1L, name = ProductName("개별상품$it"), price = Money(10_000)))
            inventoryRepository.save(Inventory.createFor(p.id, 10_000_000))
            p
        }
        fun command(productId: Long) = OrderCreateCommand(
            userId = 1L,
            items = listOf(OrderLineCommand(productId = productId, quantity = 1, price = 10_000)),
            expectedOriginalAmount = 10_000,
            expectedDiscountAmount = 0,
        )

        repeat(100) { orderFacade.place(command(sameProduct.id)) }

        val sameWall = measureNanos {
            runConcurrently(threadCount = 10) { orderFacade.place(command(sameProduct.id)) }
        }
        val distinctWall = measureNanos {
            runConcurrently(threadCount = 10) { index -> orderFacade.place(command(distinctProducts[index].id)) }
        }

        println("[측정] 같은 상품 10건 동시 주문 벽시계: %.2f ms".format(sameWall / 1_000_000.0))
        println("[측정] 다른 상품 10건 동시 주문 벽시계: %.2f ms".format(distinctWall / 1_000_000.0))
    }

    private fun runLockSegment(tx: TransactionTemplate, productId: Long, snapshot: OrderItemSnapshot) {
        tx.execute {
            val inventory = inventoryRepository.findAllByProductIdInForUpdate(listOf(productId)).first()
            inventory.decrease(1)
            orderRepository.save(Order.create(1L, listOf(snapshot)))
        }
    }

    private inline fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun printStats(label: String, nanos: LongArray) {
        val sorted = nanos.sorted()
        fun ms(n: Long) = n / 1_000_000.0
        println(
            "[측정] %s — p50: %.3f ms / p95: %.3f ms / max: %.3f ms".format(
                label,
                ms(sorted[sorted.size / 2]),
                ms(sorted[(sorted.size * 95) / 100]),
                ms(sorted.last()),
            ),
        )
    }
}

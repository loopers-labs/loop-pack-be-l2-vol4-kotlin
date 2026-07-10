package com.loopers.useractivity.application

import com.loopers.brand.domain.Brand
import com.loopers.brand.domain.BrandName
import com.loopers.brand.domain.BrandRepository
import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.order.application.OrderCreateCommand
import com.loopers.order.application.OrderFacade
import com.loopers.order.application.OrderLineCommand
import com.loopers.outbox.infrastructure.OutboxEventJpaRepository
import com.loopers.product.application.ProductService
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.awaitUntil
import com.loopers.useractivity.domain.UserActionType
import com.loopers.useractivity.infrastructure.UserActionLogJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// AFTER_COMMIT 비동기 핸들러 검증 — 실제 커밋이 필요하므로 클래스 @Transactional 금지
@SpringBootTest
@ActiveProfiles("test")
class UserActionLogIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val productService: ProductService,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val userActionLogJpaRepository: UserActionLogJpaRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("주문이 생성되면, 커밋 후 비동기로 ORDER 행동 로그가 적재된다.")
    @Test
    fun appendsOrderActionLog_whenOrderPlaced() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        inventoryRepository.save(Inventory.createFor(product.id, 100))

        val info = orderFacade.place(
            command = OrderCreateCommand(
                userId = 1L,
                items = listOf(OrderLineCommand(productId = product.id, quantity = 1, price = 10_000)),
                expectedOriginalAmount = 10_000,
                expectedDiscountAmount = 0,
            ),
        )

        awaitUntil { userActionLogJpaRepository.count() == 1L }
        val log = userActionLogJpaRepository.findAll()[0]
        assertAll(
            { assertThat(log.userId).isEqualTo(1L) },
            { assertThat(log.actionType).isEqualTo(UserActionType.ORDER) },
            { assertThat(log.targetType).isEqualTo("ORDER") },
            { assertThat(log.targetId).isEqualTo(info.id) },
        )
    }

    @DisplayName("상품 상세를 조회하면, VIEW 행동 로그만 적재되고 outbox 에는 적재되지 않는다. (유실 허용 이벤트는 승격 안 됨)")
    @Test
    fun appendsViewActionLogOnly_whenProductDetailViewed() {
        val brand = brandRepository.save(Brand(BrandName("나이키")))
        val product = productRepository.save(Product(brandId = brand.id, name = ProductName("에어맥스"), price = Money(10_000)))

        productService.getDetail(product.id)

        awaitUntil { userActionLogJpaRepository.count() == 1L }
        val log = userActionLogJpaRepository.findAll()[0]
        assertAll(
            { assertThat(log.userId).isNull() },
            { assertThat(log.actionType).isEqualTo(UserActionType.VIEW) },
            { assertThat(log.targetType).isEqualTo("PRODUCT") },
            { assertThat(log.targetId).isEqualTo(product.id) },
            { assertThat(outboxEventJpaRepository.findAll()).isEmpty() },
        )
    }
}

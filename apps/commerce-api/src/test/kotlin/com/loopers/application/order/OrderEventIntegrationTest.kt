package com.loopers.application.order

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.order.DataPlatformClient
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDate

@SpringBootTest
@RecordApplicationEvents
class OrderEventIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockkBean(relaxUnitFun = true)
    private lateinit var dataPlatformClient: DataPlatformClient

    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("주문이 생성되면, 주문 스냅샷을 담은 OrderCreatedEvent 가 발행된다.")
    @Test
    fun place_publishesOrderCreatedEvent(events: ApplicationEvents) {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        val placed = orderFacade.place("user0", rawPassword, listOf(OrderFacade.PlaceOrderLine(product.id, 2)))

        // assert
        val published = events.stream(OrderCreatedEvent::class.java).toList()
        assertThat(published).containsExactly(
            OrderCreatedEvent(
                orderId = placed.id,
                userId = user.id,
                totalPrice = 200_000L,
                discountAmount = 0L,
                finalAmount = 200_000L,
                items = listOf(OrderCreatedEvent.OrderLine(productId = product.id, quantity = 2, lineTotal = 200_000L)),
            ),
        )
    }

    @DisplayName("주문이 생성되면, 주문 정보가 데이터 플랫폼으로 전송된다.")
    @Test
    fun place_sendsOrderToDataPlatform() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        val placed = orderFacade.place("user0", rawPassword, listOf(OrderFacade.PlaceOrderLine(product.id, 2)))

        // assert: 전송은 커밋 후 비동기로 수행된다
        verify(timeout = 3000) { dataPlatformClient.send(match { it.orderId == placed.id }) }
    }

    @DisplayName("데이터 플랫폼 전송이 실패해도, 주문 생성은 성공한다.")
    @Test
    fun place_succeedsEvenIfDataPlatformFails() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
        every { dataPlatformClient.send(any()) } throws RuntimeException("데이터 플랫폼 장애")

        // act
        val placed = orderFacade.place("user0", rawPassword, listOf(OrderFacade.PlaceOrderLine(product.id, 2)))

        // assert
        val found = orderFacade.getMyOrder("user0", rawPassword, placed.id)
        assertThat(found.id).isEqualTo(placed.id)
    }
}

package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.like.LikeFacade
import com.loopers.application.order.OrderFacade
import com.loopers.application.product.ProductFacade
import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.outbox.OutboxStatus
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class OutboxWriteIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val likeFacade: LikeFacade,
    private val productFacade: ProductFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("좋아요를 등록하면, catalog-events 토픽 대상 Outbox 행이 productId 를 키로 기록된다.")
    @Test
    fun like_writesCatalogOutbox() {
        // arrange
        val user = userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        likeFacade.like("user0", rawPassword, product.id)

        // assert
        val rows = outboxEventJpaRepository.findAll()
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.topic).isEqualTo("catalog-events")
        assertThat(row.eventType).isEqualTo("ProductLiked")
        assertThat(row.aggregateId).isEqualTo(product.id)
        assertThat(row.status).isEqualTo(OutboxStatus.PENDING)
        val payload = objectMapper.readTree(row.payload)
        assertThat(payload.get("eventId").asText()).isEqualTo(row.eventId)
        assertThat(payload.get("payload").get("userId").asLong()).isEqualTo(user.id)
        assertThat(payload.get("payload").get("productId").asLong()).isEqualTo(product.id)
    }

    @DisplayName("주문을 생성하면, order-events 토픽 대상 Outbox 행이 orderId 를 키로 기록되고 판매 수량이 담긴다.")
    @Test
    fun order_writesOrderOutbox() {
        // arrange
        userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        val placed = orderFacade.place("user0", rawPassword, listOf(OrderFacade.PlaceOrderLine(product.id, 3)))

        // assert
        val rows = outboxEventJpaRepository.findAll().filter { it.eventType == "OrderCreated" }
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.topic).isEqualTo("order-events")
        assertThat(row.aggregateId).isEqualTo(placed.id)
        val items = objectMapper.readTree(row.payload).get("payload").get("items")
        assertThat(items).hasSize(1)
        assertThat(items.first().get("productId").asLong()).isEqualTo(product.id)
        assertThat(items.first().get("quantity").asInt()).isEqualTo(3)
    }

    @DisplayName("상품 상세를 조회하면, catalog-events 토픽 대상 ProductViewed Outbox 행이 기록된다.")
    @Test
    fun view_writesCatalogOutbox() {
        // arrange
        val brand = brandService.register("Nike")
        val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

        // act
        productFacade.getProductDetail(product.id)

        // assert
        val rows = outboxEventJpaRepository.findAll().filter { it.eventType == "ProductViewed" }
        assertThat(rows).hasSize(1)
        assertThat(rows.first().topic).isEqualTo("catalog-events")
        assertThat(rows.first().aggregateId).isEqualTo(product.id)
    }
}

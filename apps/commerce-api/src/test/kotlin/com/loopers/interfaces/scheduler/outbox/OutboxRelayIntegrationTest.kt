package com.loopers.interfaces.scheduler.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.order.OrderFacade
import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.outbox.OutboxStatus
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxRelayIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userFacade: UserFacade,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val rawPassword = "Valid1!pw"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun kafkaProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { KafkaTestContainer.bootstrapServers }
        }
    }

    @BeforeAll
    fun createTopics() {
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainer.bootstrapServers),
        ).use { admin ->
            val existing = admin.listTopics().names().get()
            val toCreate = listOf("catalog-events", "order-events")
                .filter { it !in existing }
                .map { NewTopic(it, 3, 1.toShort()) }
            if (toCreate.isNotEmpty()) admin.createTopics(toCreate).all().get()
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("주문을 생성하면, 릴레이가 order-events 로 orderId 를 키로 깨끗한 JSON 봉투를 발행하고 Outbox 를 PUBLISHED 로 마킹한다.")
    @Test
    fun relay_publishesOrderEventToKafka() {
        // arrange
        newConsumer("order-events").use { consumer ->
            userFacade.signUp("user0", rawPassword, "유저0", LocalDate.of(1994, 7, 14), "user0@example.com")
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

            // act: 주문 → Outbox 기록 → @Scheduled 릴레이가 발행
            val placed = orderFacade.place("user0", rawPassword, listOf(OrderFacade.PlaceOrderLine(product.id, 3)))

            // assert: 컨슈머가 메시지를 수신
            var receivedKey: String? = null
            var receivedValue: String? = null
            await().atMost(20, TimeUnit.SECONDS).untilAsserted {
                val records = consumer.poll(Duration.ofMillis(500))
                val record = records.records("order-events").firstOrNull { it.key() == placed.id.toString() }
                assertThat(record).isNotNull
                receivedKey = record!!.key()
                receivedValue = record.value()
            }

            // 파티션 키 = orderId
            assertThat(receivedKey).isEqualTo(placed.id.toString())

            // 이중 인코딩이 아닌 "깨끗한 JSON 객체" 여야 한다 (문자열로 감싸졌다면 tree 가 객체가 아님)
            val tree = objectMapper.readTree(receivedValue)
            assertThat(tree.isObject).isTrue()
            assertThat(tree.get("eventType").asText()).isEqualTo("OrderCreated")
            assertThat(tree.get("aggregateId").asLong()).isEqualTo(placed.id)
            assertThat(tree.get("eventId").asText()).isNotBlank()
            val items = tree.get("payload").get("items")
            assertThat(items).hasSize(1)
            assertThat(items.first().get("quantity").asInt()).isEqualTo(3)

            // Outbox 는 PUBLISHED 로 마킹
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val rows = outboxEventJpaRepository.findAll().filter { it.eventType == "OrderCreated" }
                assertThat(rows).isNotEmpty()
                assertThat(rows).allMatch { it.status == OutboxStatus.PUBLISHED }
            }
        }
    }

    private fun newConsumer(topic: String): KafkaConsumer<String, String> {
        val consumer = KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainer.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        )
        consumer.subscribe(listOf(topic))
        return consumer
    }
}

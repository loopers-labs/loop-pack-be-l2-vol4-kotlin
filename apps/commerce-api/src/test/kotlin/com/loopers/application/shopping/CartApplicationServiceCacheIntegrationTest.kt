package com.loopers.application.shopping

import com.loopers.config.redis.RedisConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class CartApplicationServiceCacheIntegrationTest @Autowired constructor(
    private val cartApplicationService: CartApplicationService,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("쇼핑카트 상품 라인 수는 Redis 에 캐시되고 쇼핑카트 변경 시 무효화된다.")
    @Test
    fun countItemsCachesAndInvalidatesWhenCartChanges() {
        cartApplicationService.addItem(userId = 1L, productId = 1L, quantity = 1, stockQuantity = 10)

        val firstCount = cartApplicationService.countItems(userId = 1L)

        assertAll(
            { assertThat(firstCount).isEqualTo(1) },
            { assertThat(redisTemplate.opsForValue().get(cacheKey(1L))).isEqualTo("1") },
        )

        cartApplicationService.addItem(userId = 1L, productId = 2L, quantity = 1, stockQuantity = 10)

        assertThat(redisTemplate.opsForValue().get(cacheKey(1L))).isNull()

        val secondCount = cartApplicationService.countItems(userId = 1L)

        assertAll(
            { assertThat(secondCount).isEqualTo(2) },
            { assertThat(redisTemplate.opsForValue().get(cacheKey(1L))).isEqualTo("2") },
        )
    }

    @DisplayName("트랜잭션 안의 쇼핑카트 변경은 커밋 후 상품 라인 수 캐시를 무효화한다.")
    @Test
    fun cartChangeInsideTransactionInvalidatesCountCacheAfterCommit() {
        cartApplicationService.addItem(userId = 1L, productId = 1L, quantity = 1, stockQuantity = 10)
        cartApplicationService.countItems(userId = 1L)

        transactionTemplate.executeWithoutResult {
            cartApplicationService.addItem(userId = 1L, productId = 2L, quantity = 1, stockQuantity = 10)

            assertThat(redisTemplate.opsForValue().get(cacheKey(1L))).isEqualTo("1")
        }

        assertThat(redisTemplate.opsForValue().get(cacheKey(1L))).isNull()
    }

    private fun cacheKey(userId: Long): String =
        "shopping:cart:count:user:$userId"
}

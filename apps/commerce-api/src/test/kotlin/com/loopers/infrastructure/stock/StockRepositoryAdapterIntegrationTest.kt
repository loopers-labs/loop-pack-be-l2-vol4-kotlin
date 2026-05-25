package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StockRepositoryAdapterIntegrationTest @Autowired constructor(
    private val stockRepositoryPort: StockRepositoryPort,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save 호출 시, ")
    @Nested
    inner class Save {
        @DisplayName("id가 0인 Stock을 저장하면 INSERT된다.")
        @Test
        fun insertsStock_whenIdIsZero() {
            val saved = stockRepositoryPort.save(Stock.create(productId = 1L, quantity = 10))
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(stockJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("id가 있는 Stock을 저장하면 quantity가 UPDATE된다.")
        @Test
        fun updatesQuantity_whenIdExists() {
            val saved = stockRepositoryPort.save(Stock.create(productId = 1L, quantity = 10))
            val updated = stockRepositoryPort.save(saved.updateQuantity(50))
            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.quantity).isEqualTo(50)
        }
    }

    @DisplayName("findByProductId 호출 시, ")
    @Nested
    inner class FindByProductId {
        @DisplayName("존재하면 Stock을 반환한다.")
        @Test
        fun returnsStock_whenExists() {
            stockRepositoryPort.save(Stock.create(productId = 100L, quantity = 5))
            val found = stockRepositoryPort.findByProductId(100L)
            assertThat(found).isNotNull
            assertThat(found?.quantity).isEqualTo(5)
        }

        @DisplayName("존재하지 않으면 null을 반환한다.")
        @Test
        fun returnsNull_whenMissing() {
            assertThat(stockRepositoryPort.findByProductId(9999L)).isNull()
        }
    }

    @DisplayName("delete 호출 시, ")
    @Nested
    inner class Delete {
        @DisplayName("Stock이 soft delete되어 일반 조회로 보이지 않는다.")
        @Test
        fun softDeletesStock() {
            val saved = stockRepositoryPort.save(Stock.create(productId = 1L, quantity = 10))

            stockRepositoryPort.delete(saved)

            assertThat(stockJpaRepository.findById(saved.id)).isEmpty
            assertThat(stockRepositoryPort.findByProductId(1L)).isNull()
        }
    }
}

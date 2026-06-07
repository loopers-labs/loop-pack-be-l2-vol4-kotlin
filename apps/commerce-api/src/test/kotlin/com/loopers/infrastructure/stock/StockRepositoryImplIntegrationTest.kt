package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StockRepositoryImplIntegrationTest @Autowired constructor(
    private val stockRepository: StockRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("재고 저장 시, ")
    @Nested
    inner class Save {
        @DisplayName("기존 재고의 수량 변경을 반영한다.")
        @Test
        fun save_updatesExistingStockQuantity() {
            // arrange
            val saved = stockRepository.save(Stock(productId = 1L, quantity = 10))
            val changed = Stock(id = saved.id, productId = saved.productId, quantity = 25)

            // act
            val result = stockRepository.save(changed)

            // assert
            val entity = stockJpaRepository.findByProductIdAndDeletedAtIsNull(saved.productId)
            assertAll(
                { assertThat(result.quantity).isEqualTo(25) },
                { assertThat(entity?.quantity).isEqualTo(25) },
            )
        }
    }
}

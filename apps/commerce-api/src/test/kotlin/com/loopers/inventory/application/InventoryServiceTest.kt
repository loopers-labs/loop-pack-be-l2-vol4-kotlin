package com.loopers.inventory.application

import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryErrorCode
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InventoryServiceTest {
    private val inventoryRepository: InventoryRepository = mock()
    private val inventoryService = InventoryService(inventoryRepository)

    @DisplayName("재고를 차감하면, productId를 정렬·중복 제거한 순서로 FOR UPDATE 조회 후 라인별로 차감한다.")
    @Test
    fun decreasesStock_withSortedDistinctLockOrder() {
        val inventory1 = Inventory.createFor(1L, 10)
        val inventory2 = Inventory.createFor(2L, 10)
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(1L, 2L)))
            .thenReturn(listOf(inventory1, inventory2))

        inventoryService.decreaseStock(
            listOf(
                StockDecreaseLine(productId = 2L, quantity = 3),
                StockDecreaseLine(productId = 1L, quantity = 2),
                StockDecreaseLine(productId = 2L, quantity = 1),
            ),
        )

        assertAll(
            { verify(inventoryRepository).findAllByProductIdInForUpdate(listOf(1L, 2L)) },
            { assertThat(inventory1.quantity).isEqualTo(8) },
            { assertThat(inventory2.quantity).isEqualTo(6) },
        )
    }

    @DisplayName("재고 행이 없는 상품이 포함되면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenInventoryMissing() {
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(1L))).thenReturn(emptyList())

        val result = assertThrows<NotFoundException> {
            inventoryService.decreaseStock(listOf(StockDecreaseLine(productId = 1L, quantity = 1)))
        }
        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.INVENTORY_NOT_FOUND)
    }

    @DisplayName("재고가 부족하면, CONFLICT(재고 부족) 예외가 발생한다.")
    @Test
    fun throwsConflict_whenStockInsufficient() {
        val inventory = Inventory.createFor(1L, 1)
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(1L))).thenReturn(listOf(inventory))

        val result = assertThrows<ConflictException> {
            inventoryService.decreaseStock(listOf(StockDecreaseLine(productId = 1L, quantity = 5)))
        }
        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.STOCK_INSUFFICIENT)
    }
}

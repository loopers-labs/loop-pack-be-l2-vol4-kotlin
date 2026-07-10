package com.loopers.inventory.application

import com.loopers.inventory.domain.InventoryErrorCode
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
) {
    // FOR UPDATE 는 ID 정렬 순서로 — 교차 주문 데드락 차단
    @Transactional
    fun decreaseStock(lines: List<StockDecreaseLine>) {
        val inventories = inventoryRepository
            .findAllByProductIdInForUpdate(lines.map { it.productId }.distinct().sorted())
            .associateBy { it.productId }
        lines.forEach { line ->
            val inventory = inventories[line.productId]
                ?: throw NotFoundException(InventoryErrorCode.INVENTORY_NOT_FOUND)
            inventory.decrease(line.quantity)
        }
    }

    @Transactional
    fun increaseStock(lines: List<StockRestoreLine>) {
        val inventories = inventoryRepository
            .findAllByProductIdInForUpdate(lines.map { it.productId }.distinct().sorted())
            .associateBy { it.productId }
        lines.forEach { line ->
            val inventory = inventories[line.productId]
                ?: throw NotFoundException(InventoryErrorCode.INVENTORY_NOT_FOUND)
            inventory.increase(line.quantity)
        }
    }
}

data class StockDecreaseLine(
    val productId: Long,
    val quantity: Long,
)

data class StockRestoreLine(
    val productId: Long,
    val quantity: Long,
)

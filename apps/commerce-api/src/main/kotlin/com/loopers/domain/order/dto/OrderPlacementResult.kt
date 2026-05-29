package com.loopers.domain.order.dto

import com.loopers.domain.inventory.Inventory
import com.loopers.domain.order.Order

data class OrderPlacementResult(
    val order: Order,
    val inventories: List<Inventory>,
)

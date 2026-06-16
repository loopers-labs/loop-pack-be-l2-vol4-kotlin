package com.loopers.domain.order.dto

import com.loopers.domain.coupon.model.CouponIssue
import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.order.model.Order

data class OrderPlacementResult(
    val order: Order,
    val inventories: List<Inventory>,
    val couponIssue: CouponIssue? = null,
)

package com.loopers.domain.order.dto

import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.inventory.Inventory
import com.loopers.domain.order.Order

data class OrderPlacementResult(
    val order: Order,
    val inventories: List<Inventory>,
    val couponIssue: CouponIssue? = null,
)

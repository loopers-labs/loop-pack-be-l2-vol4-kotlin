package com.loopers.application.catalog.port

interface OrderReservationQueryPort {
    fun getActiveReservedQuantity(productId: Long): Int
}

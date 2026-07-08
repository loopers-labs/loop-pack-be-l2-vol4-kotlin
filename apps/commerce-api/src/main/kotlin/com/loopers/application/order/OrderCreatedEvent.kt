package com.loopers.application.order

data class OrderCreatedEvent(val orderId: Long, val userId: Long)

package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OrderPaidItemPayload(
    val productId: Long,
    val quantity: Long,
)

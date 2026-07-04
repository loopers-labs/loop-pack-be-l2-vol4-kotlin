package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OrderPaidPayload(
    val items: List<OrderPaidItemPayload> = emptyList(),
)

package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ProductViewedPayload(
    val productId: Long? = null,
)

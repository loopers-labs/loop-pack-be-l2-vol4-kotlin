package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class LikeCountChangedPayload(
    val productId: Long? = null,
    val userId: Long? = null,
    val delta: Int? = null,
)

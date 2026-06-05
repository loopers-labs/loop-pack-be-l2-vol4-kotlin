package com.loopers.domain.like.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class LikeJpaId(
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "product_id", nullable = false)
    var productId: Long = 0,
) : Serializable

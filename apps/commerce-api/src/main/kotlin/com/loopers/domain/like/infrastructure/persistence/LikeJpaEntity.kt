package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.model.LikeModel
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "likes")
class LikeJpaEntity(
    @EmbeddedId
    var id: LikeJpaId,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }

    fun toDomain(): LikeModel = LikeModel.of(
        userId = id.userId,
        productId = id.productId,
    )

    companion object {
        fun fromDomain(like: LikeModel): LikeJpaEntity = LikeJpaEntity(
            id = LikeJpaId(
                userId = like.userId,
                productId = like.productId,
            ),
        )
    }
}

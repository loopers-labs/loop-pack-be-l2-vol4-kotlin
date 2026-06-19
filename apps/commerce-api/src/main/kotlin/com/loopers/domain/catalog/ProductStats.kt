package com.loopers.domain.catalog

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_stats",
    indexes = [
        Index(
            name = "idx_product_stats_deleted_like_count_product_id",
            columnList = "deleted_at, like_count DESC, product_id",
        ),
    ],
)
class ProductStats(
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
) : BaseEntity() {
    init {
        if (likeCount < 0) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만일 수 없습니다.")
    }

    fun increaseLikeCount() {
        likeCount += 1
    }

    fun decreaseLikeCount() {
        if (likeCount == 0L) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만일 수 없습니다.")
        likeCount -= 1
    }
}

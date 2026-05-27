package com.loopers.domain.like

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_likes_user_product", columnNames = ["user_id", "product_id"])],
)
class LikeModel(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "회원 ID는 양수여야 합니다.")
        if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 ID는 양수여야 합니다.")
    }
}

package com.loopers.infrastructure.product

import com.loopers.domain.product.LikeCountQueryPort
import org.springframework.stereotype.Component

/**
 * 05 Like 도메인 구현 전 임시 구현체. 항상 0을 반환한다.
 * 05 완료 시 실제 LikeCountQueryAdapter로 교체된다.
 */
@Component
class StubLikeCountQueryAdapter : LikeCountQueryPort {
    override fun countByProductId(productId: Long): Long = 0L
}

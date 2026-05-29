package com.loopers.application.like

import com.loopers.domain.like.LikeErrorCode
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor
import com.loopers.support.error.ForbiddenException
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val productRepository: ProductRepository,
    private val productLikeRepository: ProductLikeRepository,
) {
    /**
     * 좋아요 등록. 멱등 — 이미 좋아요한 경우 저장/증가 없이 no-op.
     * 신규 INSERT 시에만 [com.loopers.domain.product.Product.like]로 like_count 동기 증가(dirty checking flush).
     * TODO(동시성): existsBy 선검사는 TOCTOU race가 있다. 동시성 메커니즘은 본 과제 비범위(R1) — 추후 UK 위반 변환/락으로 보강.
     */
    @Transactional
    fun like(userId: Long, productId: Long) {
        val product = productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }
        productLikeRepository.save(ProductLike(userId, productId))
        product.like()
    }

    /**
     * 좋아요 취소. 멱등 — 좋아요한 적 없으면 no-op(hard delete). 실제 삭제 시에만 like_count 동기 감소.
     */
    @Transactional
    fun unlike(userId: Long, productId: Long) {
        val product = productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
            ?: return
        productLikeRepository.delete(productLike)
        product.unlike()
    }

    // 단일 쿼리 read + lazy 없음이라 service 레벨 read 트랜잭션 불필요(docs/week3/06, BrandService.get/list 결정과 동일).
    fun findMine(requesterId: Long, userId: Long, cursor: IdCursor?, size: Int): CursorPage<LikeInfo> {
        if (requesterId != userId) {
            throw ForbiddenException(LikeErrorCode.FORBIDDEN_LIKE_ACCESS)
        }
        val page = productLikeRepository.findAllByUserId(userId, cursor, size)
        return CursorPage(page.content.map(LikeInfo::from), page.hasNext, page.nextCursor)
    }
}

data class LikeInfo(
    val id: Long,
    val productId: Long,
) {
    companion object {
        fun from(productLike: ProductLike): LikeInfo =
            LikeInfo(
                id = productLike.id,
                productId = productLike.productId,
            )
    }
}

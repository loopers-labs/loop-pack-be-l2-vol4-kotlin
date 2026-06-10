package com.loopers.application.like

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeCountEventHandler(
    private val productRepository: ProductRepository,
) {
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeCreatedEvent) {
        val product = productRepository.findActiveById(event.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        product.incrementLikeCount()
    }

    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeDeletedEvent) {
        val product = productRepository.findActiveById(event.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        product.decrementLikeCount()
    }
}

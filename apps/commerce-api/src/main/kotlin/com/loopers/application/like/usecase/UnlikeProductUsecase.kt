package com.loopers.application.like.usecase

import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UnlikeProductUsecase(
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val likeRepository: LikeRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(command: LikeProductCommand) {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        if (!productRepository.existsActiveById(command.productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        }

        val like = likeRepository.findByUserIdAndProductId(userId = user.id, productId = command.productId) ?: return
        likeRepository.delete(like)
        eventPublisher.publishEvent(LikeDeletedEvent(productId = command.productId))
    }
}

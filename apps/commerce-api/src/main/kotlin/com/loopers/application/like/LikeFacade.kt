package com.loopers.application.like

import com.loopers.application.product.ProductApplicationService
import com.loopers.application.user.UserApplicationService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeApplicationService: LikeApplicationService,
    private val productApplicationService: ProductApplicationService,
    private val userApplicationService: UserApplicationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun addLike(userId: Long, productId: Long): LikeResultInfo {
        userApplicationService.getUser(userId)
        productApplicationService.getProduct(productId)

        val changed = likeApplicationService.activate(userId = userId, productId = productId)

        if (changed) {
            eventPublisher.publishEvent(LikeChangedEvent(userId = userId, productId = productId, activated = true))
        }

        return LikeResultInfo(userId = userId, productId = productId, changed = changed)
    }

    @Transactional
    fun cancelLike(userId: Long, productId: Long): LikeResultInfo {
        userApplicationService.getUser(userId)
        productApplicationService.getProduct(productId)

        val changed = likeApplicationService.cancel(userId = userId, productId = productId)

        if (changed) {
            eventPublisher.publishEvent(LikeChangedEvent(userId = userId, productId = productId, activated = false))
        }

        return LikeResultInfo(userId = userId, productId = productId, changed = changed)
    }
}

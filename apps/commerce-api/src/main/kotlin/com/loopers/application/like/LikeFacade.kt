package com.loopers.application.like

import com.loopers.application.product.ProductService
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.like.ProductLikeService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
    private val productStatService: ProductStatService,
    private val productLikeService: ProductLikeService,
) {
    @Transactional
    fun like(memberId: Long, productId: Long) {
        val product = productService.getDisplayableProduct(productId)
        val created = likeService.like(memberId = memberId, productId = product.id)

        if (created) {
            val productStat = productStatService.getProductStat(product.id)
            productLikeService.like(productStat)
            productStatService.save(productStat)
        }
    }

    @Transactional
    fun unlike(memberId: Long, productId: Long) {
        val product = productService.getDisplayableProduct(productId)
        val deleted = likeService.unlike(memberId = memberId, productId = product.id)

        if (deleted) {
            val productStat = productStatService.getProductStat(product.id)
            productLikeService.unlike(productStat)
            productStatService.save(productStat)
        }
    }
}

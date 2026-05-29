package com.loopers.application.like

import com.loopers.application.product.ProductService
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
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

    @Transactional(readOnly = true)
    fun getLikedProducts(memberId: Long, userId: Long, page: Int, size: Int): Page<ProductSummary> {
        if (memberId != userId) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Cannot access another member's likes.")
        }

        return likeService.getLikedProducts(memberId = memberId, page = page, size = size)
    }
}

package com.loopers.application.like

import com.loopers.application.brand.BrandService
import com.loopers.application.product.ProductService
import com.loopers.application.productstat.ProductStatService
import com.loopers.application.user.UserService
import com.loopers.domain.like.service.ProductLikeService
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val userService: UserService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val productStatService: ProductStatService,
    private val productLikeService: ProductLikeService,
) {
    @Transactional
    fun like(loginId: String, rawPassword: String, productId: Long) {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val product = productService.getProduct(productId)
        val created = likeService.like(memberId = user.id, productId = product.id)

        if (created) {
            productStatService.increaseLikeCount(product.id)
        }
    }

    @Transactional
    fun unlike(loginId: String, rawPassword: String, productId: Long) {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val product = productService.getProduct(productId)
        val deleted = likeService.unlike(memberId = user.id, productId = product.id)

        if (deleted) {
            productStatService.decreaseLikeCount(product.id)
        }
    }

    @Transactional(readOnly = true)
    fun getLikedProducts(loginId: String, rawPassword: String, userId: Long): List<ProductSummary> {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        if (user.id != userId) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Cannot access another member's likes.")
        }

        val likes = likeService.getLikes(user.id)
        val products = productService.getProducts(likes.map { it.productId })
        val brands = brandService.getBrands(products.map { it.brandId })
        val productStats = productStatService.getProductStats(products.map { it.id })

        return productLikeService.displayLikedProductSummaries(
            likes = likes,
            products = products,
            brands = brands,
            productStats = productStats,
        )
    }
}

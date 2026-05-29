package com.loopers.application.like

import com.loopers.application.brand.BrandService
import com.loopers.application.product.ProductService
import com.loopers.application.productstat.ProductStatService
import com.loopers.application.user.UserService
import com.loopers.domain.like.ProductLikeService
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
        val product = productService.getDisplayableProduct(productId)
        val created = likeService.like(memberId = user.id, productId = product.id)

        if (created) {
            val productStat = productStatService.getProductStat(product.id)
            productLikeService.like(productStat)
            productStatService.save(productStat)
        }
    }

    @Transactional
    fun unlike(loginId: String, rawPassword: String, productId: Long) {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val product = productService.getDisplayableProduct(productId)
        val deleted = likeService.unlike(memberId = user.id, productId = product.id)

        if (deleted) {
            val productStat = productStatService.getProductStat(product.id)
            productLikeService.unlike(productStat)
            productStatService.save(productStat)
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
        val displayableProducts = productLikeService.displayLikedProducts(likes = likes, products = products)
        val brandById = brandService.getBrands(displayableProducts.map { it.brandId })
            .filter { !it.isDeleted }
            .associateBy { it.id }
        val statByProductId = productStatService.getProductStats(displayableProducts.map { it.id })
            .associateBy { it.productId }

        return displayableProducts.mapNotNull { product ->
            val brand = brandById[product.brandId] ?: return@mapNotNull null
            val productStat = statByProductId[product.id] ?: productStatService.emptyStat(product.id)

            ProductSummary(
                productId = product.id,
                productName = product.name,
                price = product.price,
                imageUrl = product.imageUrl,
                brandId = brand.id,
                brandName = brand.name,
                likeCount = productStat.likeCount,
            )
        }
    }
}

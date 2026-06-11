package com.loopers.application.like

import com.loopers.domain.brand.BrandService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikedProductSummary
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.like.LikeApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeApplicationServiceAdapter(
    private val likeService: LikeService,
    private val productService: ProductService,
    private val userService: UserService,
    private val brandService: BrandService,
) : LikeApplicationServicePort {
    @Transactional
    override fun like(userId: Long, productId: Long) {
        productService.getById(productId)
        likeService.register(userId, productId)
    }

    @Transactional
    override fun unlike(userId: Long, productId: Long) {
        productService.getById(productId)
        likeService.cancel(userId, productId)
    }

    @Transactional(readOnly = true)
    override fun getLikedProducts(
        targetUserId: Long,
        requesterUserId: Long,
        pageRequest: PageRequest,
    ): PageResult<LikedProductSummary> {
        userService.getById(targetUserId)
        if (targetUserId != requesterUserId) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.")
        }

        val likesPage = likeService.findAllByUserId(targetUserId, pageRequest)
        if (likesPage.items.isEmpty()) {
            return PageResult.of(
                items = emptyList(),
                pageRequest = pageRequest,
                totalElements = likesPage.totalElements,
            )
        }

        val productIds = likesPage.items.map { it.productId }
        val productsById = productService.findAllByIds(productIds).associateBy { it.id }
        val brandIds = productsById.values.map { it.brandId }.distinct()
        val brandsById = brandService.findAllByIds(brandIds).associateBy { it.id }

        val summaries = likesPage.items.mapNotNull { like ->
            val product = productsById[like.productId] ?: return@mapNotNull null
            val brand = brandsById[product.brandId] ?: return@mapNotNull null
            LikedProductSummary.of(product, brand)
        }
        return PageResult.of(
            items = summaries,
            pageRequest = pageRequest,
            totalElements = likesPage.totalElements,
        )
    }
}

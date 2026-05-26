package com.loopers.application.like

import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.user.UserRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productRepositoryPort: ProductRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val brandRepositoryPort: BrandRepositoryPort,
) {
    @Transactional
    fun like(userId: Long, productId: Long) {
        productRepositoryPort.findByIdOrNull(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        likeService.register(userId, productId)
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        productRepositoryPort.findByIdOrNull(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        likeService.cancel(userId, productId)
    }

    @Transactional(readOnly = true)
    fun getLikedProducts(
        targetUserId: Long,
        requesterUserId: Long,
        pageRequest: PageRequest,
    ): PageResult<LikedProductSummary> {
        userRepositoryPort.findByIdOrNull(targetUserId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
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
        val productsById = productRepositoryPort.findAllByIds(productIds).associateBy { it.id }
        val brandIds = productsById.values.map { it.brandId }.distinct()
        val brandsById = brandRepositoryPort.findAllByIds(brandIds).associateBy { it.id }

        val summaries = likesPage.items.mapNotNull { like ->
            val product = productsById[like.productId] ?: return@mapNotNull null
            val brand = brandsById[product.brandId] ?: return@mapNotNull null
            LikedProductSummary(
                productId = product.id,
                name = product.name,
                price = product.price,
                brandName = brand.name,
            )
        }
        return PageResult.of(
            items = summaries,
            pageRequest = pageRequest,
            totalElements = likesPage.totalElements,
        )
    }
}

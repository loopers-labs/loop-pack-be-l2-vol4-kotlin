package com.loopers.application.like

import com.loopers.application.product.ProductInfo
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeModel
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.ProductLikedEvent
import com.loopers.domain.like.ProductUnlikedEvent
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val userService: UserService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeRepository: LikeRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun like(loginId: String, rawPassword: String, productId: Long) {
        val user = userService.authenticate(loginId, rawPassword)
        val product = productService.getActiveById(productId)
        if (likeRepository.existsByUserIdAndProductId(user.id, product.id)) return
        likeRepository.save(LikeModel(userId = user.id, productId = product.id))
        eventPublisher.publishEvent(ProductLikedEvent(userId = user.id, productId = product.id))
    }

    @Transactional
    fun unlike(loginId: String, rawPassword: String, productId: Long) {
        val user = userService.authenticate(loginId, rawPassword)
        val affected = likeRepository.deleteByUserIdAndProductId(user.id, productId)
        if (affected == 0) return
        eventPublisher.publishEvent(ProductUnlikedEvent(userId = user.id, productId = productId))
    }

    @Transactional(readOnly = true)
    fun getMyLikedProducts(
        loginId: String,
        rawPassword: String,
        targetUserId: Long,
        pageable: Pageable,
    ): Page<ProductInfo> {
        val user = userService.authenticate(loginId, rawPassword)
        if (user.id != targetUserId) {
            throw CoreException(ErrorType.UNAUTHORIZED, "본인의 좋아요 목록만 조회할 수 있습니다.")
        }
        val likesPage = likeRepository.findAllByUserId(user.id, pageable)
        val productIds = likesPage.content.map { it.productId }
        val productsById = productService.findActiveByIds(productIds).associateBy { it.id }
        val brandIds = productsById.values.map { it.brandId }.distinct()
        val brandsById = brandService.findAllActiveByIds(brandIds).associateBy { it.id }

        val content = likesPage.content.mapNotNull { like ->
            val product = productsById[like.productId] ?: return@mapNotNull null
            val brand = brandsById[product.brandId] ?: return@mapNotNull null
            ProductInfo.from(product, brand)
        }
        return PageImpl(content, pageable, likesPage.totalElements)
    }
}

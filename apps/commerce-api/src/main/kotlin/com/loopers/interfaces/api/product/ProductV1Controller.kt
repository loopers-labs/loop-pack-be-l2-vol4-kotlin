package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.ranking.RankingQueryService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 API 컨트롤러.
 * 상품 상세 조회 시 오늘자 랭킹 순위를 함께 반환한다.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
    private val rankingQueryService: RankingQueryService,
) : ProductV1ApiSpec {

    /** 상품 목록 조회 */
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false, defaultValue = "latest") sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<*> {
        val sortType = ProductSortType.from(sort ?: "latest")
        val pageable = PageRequest.of(page, size)
        return productFacade.getProducts(brandId, sortType, pageable)
            .map { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    /** 상품 상세 조회 (랭킹 정보 포함) */
    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val detail = productFacade.getProductDetail(productId)
        val rankingInfo = rankingQueryService.getProductRank(productId)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail, rankingInfo))
    }
}

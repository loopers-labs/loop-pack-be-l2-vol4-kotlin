package com.loopers.interfaces.api.catalog

import com.loopers.application.catalog.CatalogApplicationService
import com.loopers.application.catalog.ProductQueryFacade
import com.loopers.application.catalog.ProductSort
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.AuthenticationInterceptor
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(
    private val catalogApplicationService: CatalogApplicationService,
    private val productQueryFacade: ProductQueryFacade,
    private val request: HttpServletRequest,
) {
    @GetMapping("/{brandId}")
    fun getBrand(@PathVariable brandId: Long): ApiResponse<CatalogV1Dto.BrandDetailResponse> {
        val brand = catalogApplicationService.getBrandInfo(brandId)
        val products = productQueryFacade.getBrandProducts(
            brandId = brandId,
            sort = ProductSort.LATEST,
            page = 0,
            size = 100,
            userId = currentUserId(),
        )
        return CatalogV1Dto.BrandDetailResponse.from(brand, products)
            .let(ApiResponse.Companion::success)
    }

    @GetMapping("/{brandId}/products")
    fun getBrandProducts(
        @PathVariable brandId: Long,
        @RequestParam(defaultValue = "latest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CatalogV1Dto.ProductDisplayResponse>> =
        productQueryFacade.getBrandProducts(
            brandId = brandId,
            sort = parseSort(sort),
            page = page,
            size = size,
            userId = currentUserId(),
        )
            .map(CatalogV1Dto.ProductDisplayResponse::from)
            .let(ApiResponse.Companion::success)

    private fun currentUserId(): Long? =
        (request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY) as? User)?.id

    private fun parseSort(sort: String): ProductSort =
        when (sort) {
            "latest" -> ProductSort.LATEST
            "price_asc" -> ProductSort.PRICE_ASC
            "likes_desc" -> ProductSort.LIKES_DESC
            else -> throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬입니다.")
        }
}

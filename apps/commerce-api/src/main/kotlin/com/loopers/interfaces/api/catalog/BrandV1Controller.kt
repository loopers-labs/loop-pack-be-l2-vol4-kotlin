package com.loopers.interfaces.api.catalog

import com.loopers.application.catalog.CatalogApplicationService
import com.loopers.application.catalog.ProductQueryFacade
import com.loopers.application.catalog.ProductSort
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.AuthenticationInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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

    private fun currentUserId(): Long? =
        (request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY) as? User)?.id
}

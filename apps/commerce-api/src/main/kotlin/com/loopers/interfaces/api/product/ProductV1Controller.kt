package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>> {
        val productPage = productFacade.getProducts(
            ProductListCommand(
                brandId = brandId,
                sort = ProductSort.from(sort),
                page = page,
                size = size,
            ),
        )

        return productPage
            .map(ProductV1Dto.ProductSummaryResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse> {
        return productFacade.getProduct(productId)
            .let { ProductV1Dto.ProductDetailResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}

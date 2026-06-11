package com.loopers.interfaces.api.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productApplicationService: ProductApplicationServicePort,
) {
    @GetMapping
    fun getProducts(
        @RequestParam(name = "brandId", required = false) brandId: Long?,
        @RequestParam(name = "sort", required = false) sort: String?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): ApiResponse<PageView<ProductV1Dto.ProductSummaryResponse>> {
        val result = productApplicationService.getProducts(
            brandId,
            ProductSort.from(sort),
            PageRequest(page = page, size = size),
        )
        return ApiResponse.success(PageView.from(result, ProductV1Dto.ProductSummaryResponse::from))
    }

    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val detail = productApplicationService.getProduct(id)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }
}

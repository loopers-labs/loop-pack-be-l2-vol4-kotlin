package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productFacade: ProductFacade,
) {
    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val detail = productFacade.getProduct(id)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }
}

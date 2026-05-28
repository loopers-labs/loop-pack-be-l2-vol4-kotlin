package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productApplicationService: ProductApplicationServicePort,
) {
    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val detail = productApplicationService.getProduct(id)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }
}

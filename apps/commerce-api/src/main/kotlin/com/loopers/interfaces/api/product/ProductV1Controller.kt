package com.loopers.interfaces.api.product

import com.loopers.application.product.usecase.GetProductDetailUsecase
import com.loopers.application.product.usecase.GetProductsUsecase
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val getProductsUsecase: GetProductsUsecase,
    private val getProductDetailUsecase: GetProductDetailUsecase,
) {
    @GetMapping
    fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
    ): ApiResponse<List<ProductV1Dto.ProductResponse>> {
        return getProductsUsecase.execute(
            GetProductsUsecase.Query(
                brandId = brandId,
                sort = ProductSort.from(sort),
            ),
        ).map { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        return getProductDetailUsecase.execute(productId)
            .let { ProductV1Dto.ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}

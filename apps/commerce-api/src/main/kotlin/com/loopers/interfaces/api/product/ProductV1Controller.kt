package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun createProduct(
        @Valid @RequestBody request: ProductV1Dto.CreateRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val info = productFacade.createProduct(
            brandId = request.brandId,
            name = request.name,
            description = request.description,
            price = request.price,
            initialStock = request.initialStock,
        )
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info))
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val info = productFacade.getProductDetail(productId)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info))
    }

    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(defaultValue = "LIKES_DESC") sort: ProductSortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>> {
        val condition = ProductV1Dto.toSearchCondition(brandId, sort, page, size)
        val result = productFacade.getProducts(condition)
        return ApiResponse.success(
            PageResponse.from(result) { ProductV1Dto.ProductSummaryResponse.from(it) },
        )
    }

    @PutMapping("/{productId}")
    override fun updateProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductV1Dto.UpdateRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        val info = productFacade.updateProduct(
            productId = productId,
            name = request.name,
            description = request.description,
            price = request.price,
        )
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info))
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun deleteProduct(
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        productFacade.deleteProduct(productId)
        return ApiResponse.success()
    }
}

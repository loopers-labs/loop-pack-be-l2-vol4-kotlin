package com.loopers.domain.product.presentation

import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.presentation.response.ProductResponse
import com.loopers.interfaces.api.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
@Validated
class ProductController(
    private val productFacade: ProductFacade,
) : ProductApiSpec {
    @GetMapping
    override fun findProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<List<ProductResponse>> {
        return productFacade.findProducts(
            ProductSearchCommand.of(
                brandId = brandId,
                sort = sort,
                page = page,
                size = size,
            ),
        )
            .map { ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductResponse> {
        return productFacade.getProduct(productId)
            .let { ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}

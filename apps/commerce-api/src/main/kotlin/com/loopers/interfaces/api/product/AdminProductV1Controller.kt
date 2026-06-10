package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/products")
class AdminProductV1Controller(
    private val productFacade: ProductFacade,
) : AdminProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>> {
        LoopersHeaders.validateAdmin(adminId)

        return productFacade.getProducts(
            ProductListCommand(
                brandId = brandId,
                sort = ProductSort.from(sort),
                page = page,
                size = size,
            ),
        )
            .map(AdminProductV1Dto.ProductSummaryResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable productId: Long,
    ): ApiResponse<AdminProductV1Dto.ProductDetailResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return productFacade.getProductForAdmin(productId)
            .let(AdminProductV1Dto.ProductDetailResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    override fun createProduct(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestBody request: AdminProductV1Dto.CreateProductRequest,
    ): ApiResponse<AdminProductV1Dto.ProductDetailResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return productFacade.createProduct(request.toCommand())
            .let(AdminProductV1Dto.ProductDetailResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{productId}")
    override fun updateProduct(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable productId: Long,
        @RequestBody request: AdminProductV1Dto.UpdateProductRequest,
    ): ApiResponse<AdminProductV1Dto.ProductDetailResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return productFacade.updateProduct(productId = productId, command = request.toCommand())
            .let(AdminProductV1Dto.ProductDetailResponse::from)
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{productId}")
    override fun deleteProduct(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        LoopersHeaders.validateAdmin(adminId)

        productFacade.deleteProduct(productId)
        return ApiResponse.success()
    }
}

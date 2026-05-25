package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
class ProductAdminController(
    private val productFacade: ProductFacade,
) {
    @GetMapping
    fun getProducts(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestParam(name = "brandId", required = false) brandId: Long?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): ApiResponse<ProductAdminV1Dto.ProductsResponse> {
        verifyAdmin(ldap)
        val result = productFacade.getProducts(brandId, PageRequest(page = page, size = size))
        return ApiResponse.success(ProductAdminV1Dto.ProductsResponse.from(result))
    }

    @GetMapping("/{id}")
    fun getProduct(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        verifyAdmin(ldap)
        val detail = productFacade.getProduct(id)
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }

    @PostMapping
    fun createProduct(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestBody request: ProductAdminV1Dto.CreateProductRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        verifyAdmin(ldap)
        val detail = productFacade.createProduct(request.toCommand())
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }

    @PutMapping("/{id}")
    fun updateProduct(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
        @RequestBody request: ProductAdminV1Dto.UpdateProductRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse> {
        verifyAdmin(ldap)
        val detail = productFacade.updateProduct(request.toCommand(id))
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(detail))
    }

    @DeleteMapping("/{id}")
    fun deleteProduct(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<Any> {
        verifyAdmin(ldap)
        productFacade.deleteProduct(id)
        return ApiResponse.success()
    }

    private fun verifyAdmin(ldap: String?) {
        if (ldap != ADMIN_LDAP) {
            throw CoreException(ErrorType.FORBIDDEN, "어드민 권한이 없습니다.")
        }
    }

    companion object {
        private const val ADMIN_LDAP = "loopers.admin"
    }
}

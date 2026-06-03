package com.loopers.interfaces.api.order

import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageView
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/orders")
class OrderAdminController(
    private val orderAdminApplicationService: OrderAdminApplicationServicePort,
) {
    @GetMapping
    fun getOrders(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): ApiResponse<PageView<OrderAdminV1Dto.AdminOrderSummaryResponse>> {
        verifyAdmin(ldap)
        val result = orderAdminApplicationService.getOrders(PageRequest(page = page, size = size))
        return ApiResponse.success(PageView.from(result, OrderAdminV1Dto.AdminOrderSummaryResponse::from))
    }

    @GetMapping("/{id}")
    fun getOrder(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<OrderAdminV1Dto.AdminOrderDetailResponse> {
        verifyAdmin(ldap)
        val detail = orderAdminApplicationService.getOrder(id)
        return ApiResponse.success(OrderAdminV1Dto.AdminOrderDetailResponse.from(detail))
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

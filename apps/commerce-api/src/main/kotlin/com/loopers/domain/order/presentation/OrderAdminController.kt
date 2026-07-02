package com.loopers.domain.order.presentation

import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.presentation.response.OrderResponse
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/orders")
@Validated
class OrderAdminController(
    private val orderFacade: OrderFacade,
) : OrderAdminApiSpec {
    @GetMapping
    override fun findAdminOrders(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<List<OrderResponse>> {
        requireAdmin(ldap)
        return orderFacade.findAdminOrders(page ?: DEFAULT_PAGE, size ?: DEFAULT_SIZE)
            .map { OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun findAdminOrder(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderResponse> {
        requireAdmin(ldap)
        return orderFacade.findAdminOrder(orderId)
            .let { OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    private fun requireAdmin(ldap: String?) {
        if (ldap.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
    }

    companion object {
        private const val ADMIN_LDAP_HEADER = "X-Loopers-Ldap"
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
    }
}

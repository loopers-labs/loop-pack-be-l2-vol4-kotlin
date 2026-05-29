package com.loopers.interfaces.api.shopping

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Cart V1 API", description = "Loopers shopping cart API.")
interface CartV1ApiSpec {
    @Operation(summary = "쇼핑카트 조회")
    fun getCart(user: User): ApiResponse<CartV1Dto.CartResponse>

    @Operation(summary = "쇼핑카트 상품 담기")
    fun addItem(user: User, request: CartV1Dto.AddItemRequest): ApiResponse<Unit>

    @Operation(summary = "쇼핑카트 상품 수량 변경")
    fun changeQuantity(user: User, productId: Long, request: CartV1Dto.ChangeQuantityRequest): ApiResponse<Unit>

    @Operation(summary = "쇼핑카트 상품 제거")
    fun removeItem(user: User, productId: Long): ApiResponse<Unit>

    @Operation(summary = "쇼핑카트 비우기")
    fun clear(user: User): ApiResponse<Unit>

    @Operation(summary = "쇼핑카트 주문창 접근")
    fun checkout(user: User, request: CartV1Dto.CheckoutRequest): ApiResponse<OrderV1Dto.OrderResponse>
}

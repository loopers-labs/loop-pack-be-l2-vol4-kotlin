package com.loopers.interfaces.api.shopping

import com.loopers.application.shopping.CartCheckoutFacade
import com.loopers.application.shopping.CartCommand
import com.loopers.application.shopping.CartFacade
import com.loopers.application.shopping.CartQueryFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@LoginRequired
@RestController
@RequestMapping("/api/v1/cart")
class CartV1Controller(
    private val cartFacade: CartFacade,
    private val cartQueryFacade: CartQueryFacade,
    private val cartCheckoutFacade: CartCheckoutFacade,
) : CartV1ApiSpec {
    @GetMapping
    override fun getCart(@CurrentUser user: User): ApiResponse<CartV1Dto.CartResponse> =
        cartQueryFacade.getCart(user.id)
            .let(CartV1Dto.CartResponse::from)
            .let(ApiResponse.Companion::success)

    @PostMapping("/items")
    override fun addItem(
        @CurrentUser user: User,
        @RequestBody @Valid request: CartV1Dto.AddItemRequest,
    ): ApiResponse<Unit> {
        cartFacade.addItem(request.toCommand(user.id))
        return ApiResponse.success(Unit)
    }

    @PatchMapping("/items/{productId}")
    override fun changeQuantity(
        @CurrentUser user: User,
        @PathVariable productId: Long,
        @RequestBody @Valid request: CartV1Dto.ChangeQuantityRequest,
    ): ApiResponse<Unit> {
        cartFacade.changeQuantity(request.toCommand(user.id, productId))
        return ApiResponse.success(Unit)
    }

    @DeleteMapping("/items/{productId}")
    override fun removeItem(
        @CurrentUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<Unit> {
        cartFacade.removeItem(CartCommand.RemoveItem(userId = user.id, productId = productId))
        return ApiResponse.success(Unit)
    }

    @DeleteMapping("/items")
    override fun clear(@CurrentUser user: User): ApiResponse<Unit> {
        cartFacade.clear(CartCommand.Clear(userId = user.id))
        return ApiResponse.success(Unit)
    }

    @PostMapping("/checkout")
    override fun checkout(
        @CurrentUser user: User,
        @RequestBody @Valid request: CartV1Dto.CheckoutRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        cartCheckoutFacade.checkout(request.toCommand(user.id))
            .let(OrderV1Dto.OrderResponse::from)
            .let(ApiResponse.Companion::success)
}

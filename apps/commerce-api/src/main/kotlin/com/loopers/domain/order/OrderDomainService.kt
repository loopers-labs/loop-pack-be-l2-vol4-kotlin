package com.loopers.domain.order

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductStockModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class OrderDomainService {
    fun create(userId: Long, items: List<OrderProduct>): OrderModel {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.")
        if (items.map { it.product.id }.distinct().size != items.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "하나의 주문에 같은 상품을 중복으로 담을 수 없습니다.")
        }

        items.forEach {
            if (!it.stock.hasEnough(it.quantity)) {
                throw CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.")
            }
        }

        val orderItems = items.map {
            it.stock.deduct(it.quantity)
            it.product.syncStock(it.stock.quantity)
            it.product.toOrderItem(it.quantity)
        }

        return OrderModel(
            userId = userId,
            items = orderItems,
        )
    }

    data class OrderProduct(
        val product: ProductModel,
        val stock: ProductStockModel,
        val quantity: Int,
    )
}

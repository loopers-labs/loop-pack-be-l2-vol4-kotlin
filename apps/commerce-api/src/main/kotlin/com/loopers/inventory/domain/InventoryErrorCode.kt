package com.loopers.inventory.domain

import com.loopers.support.error.ErrorCode

enum class InventoryErrorCode(
    override val message: String,
) : ErrorCode {
    INVENTORY_NOT_FOUND("재고를 찾을 수 없습니다."),
    STOCK_INSUFFICIENT("재고가 부족합니다."),
    INVALID_QUANTITY("수량이 올바르지 않습니다."),
    ;

    override val code: String
        get() = "INVENTORY:$name"
}

package com.loopers.domain.product.model

import com.loopers.domain.product.exception.ProductNotOrderableException
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import java.time.ZonedDateTime

data class ProductModel(
    val id: Long = 0,
    val brandId: Long,
    val name: ProductName,
    val price: Money,
    val deletedAtOrNull: ZonedDateTime? = null,
) {
    fun changeName(name: ProductName): ProductModel = copy(name = name)

    fun changePrice(price: Money): ProductModel = copy(price = price)

    fun delete(): ProductModel {
        if (deletedAtOrNull != null) {
            return this
        }
        return copy(deletedAtOrNull = ZonedDateTime.now())
    }

    fun requireOrderable() {
        if (deletedAtOrNull != null) {
            throw ProductNotOrderableException(id)
        }
    }
}

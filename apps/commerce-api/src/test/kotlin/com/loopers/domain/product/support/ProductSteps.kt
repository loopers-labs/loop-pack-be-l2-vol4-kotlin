package com.loopers.domain.product.support

import com.loopers.domain.product.application.command.ProductRegisterCommand
import com.loopers.domain.product.application.command.ProductUpdateCommand
import com.loopers.domain.product.application.command.StockDecreaseCommand
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.model.StockModel
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import com.loopers.domain.product.vo.StockQuantity
import java.time.ZonedDateTime

class ProductSteps {
    companion object {
        const val 기본_상품_ID: Long = 1L
        const val 기본_브랜드_ID: Long = 10L
        const val 기본_상품명: String = "기본 상품"
        const val 기본_가격: Long = 10_000L
        const val 기본_재고: Long = 10L

        fun 상품_도메인_생성(
            id: Long = 기본_상품_ID,
            brandId: Long = 기본_브랜드_ID,
            name: String = 기본_상품명,
            price: Long = 기본_가격,
            deletedAtOrNull: ZonedDateTime? = null,
        ): ProductModel = ProductModel(
            id = id,
            brandId = brandId,
            name = ProductName.of(name),
            price = Money.of(price),
            deletedAtOrNull = deletedAtOrNull,
        )

        fun 재고_도메인_생성(
            productId: Long = 기본_상품_ID,
            leftStock: Long = 기본_재고,
        ): StockModel = StockModel.initialize(
            productId = productId,
            leftStock = StockQuantity.of(leftStock),
        )

        fun 상품_등록_커맨드(
            brandId: Long = 기본_브랜드_ID,
            name: String = 기본_상품명,
            price: Long = 기본_가격,
            initialStock: Long = 기본_재고,
        ): ProductRegisterCommand = ProductRegisterCommand(
            brandId = brandId,
            name = name,
            price = price,
            initialStock = initialStock,
        )

        fun 상품_수정_커맨드(
            name: String = 기본_상품명,
            price: Long = 기본_가격,
        ): ProductUpdateCommand = ProductUpdateCommand(
            name = name,
            price = price,
        )

        fun 재고_차감_커맨드(
            productId: Long = 기본_상품_ID,
            quantity: Long,
        ): StockDecreaseCommand = StockDecreaseCommand(
            productId = productId,
            quantity = quantity,
        )
    }
}

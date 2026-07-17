package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductSummaryInfo
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.support.paging.PageCondition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

class ProductV1Dto {
    data class CreateRequest(
        @field:Positive
        val brandId: Long,
        @field:NotBlank
        val name: String,
        @field:NotBlank
        val description: String,
        @field:Positive
        val price: Long,
        @field:PositiveOrZero
        val initialStock: Int = 0,
    )

    data class UpdateRequest(
        @field:NotBlank
        val name: String,
        @field:NotBlank
        val description: String,
        @field:Positive
        val price: Long,
    )

    data class ProductResponse(
        val id: Long,
        val brandId: Long,
        val brandName: String,
        val name: String,
        val description: String,
        val price: Long,
        val stock: Int,
        val likeCount: Int,
        val soldOut: Boolean,
        val rank: Long?,
    ) {
        companion object {
            fun from(info: ProductInfo): ProductResponse =
                ProductResponse(
                    id = info.id,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    name = info.name,
                    description = info.description,
                    price = info.price,
                    stock = info.stock,
                    likeCount = info.likeCount,
                    soldOut = info.soldOut,
                    rank = info.rank,
                )
        }
    }

    data class ProductSummaryResponse(
        val id: Long,
        val brandId: Long,
        val brandName: String,
        val name: String,
        val price: Long,
        val likeCount: Int,
        val soldOut: Boolean,
    ) {
        companion object {
            fun from(info: ProductSummaryInfo): ProductSummaryResponse =
                ProductSummaryResponse(
                    id = info.id,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    name = info.name,
                    price = info.price,
                    likeCount = info.likeCount,
                    soldOut = info.soldOut,
                )
        }
    }

    companion object {
        fun toSearchCondition(
            brandId: Long?,
            sort: ProductSortType,
            page: Int,
            size: Int,
        ): ProductSearchCondition =
            ProductSearchCondition(
                brandId = brandId,
                sortType = sort,
                pageCondition = PageCondition(page = page, size = size),
            )
    }
}

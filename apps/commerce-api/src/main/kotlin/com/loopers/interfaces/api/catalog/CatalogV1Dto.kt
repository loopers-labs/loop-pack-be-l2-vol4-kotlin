package com.loopers.interfaces.api.catalog

import com.loopers.application.catalog.CatalogInfo
import com.loopers.domain.catalog.BrandStatus
import com.loopers.domain.catalog.CatalogCommand
import com.loopers.domain.catalog.ProductStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

class CatalogV1Dto {
    data class CreateBrandRequest(
        @field:NotBlank
        val name: String,
    ) {
        fun toCommand(): CatalogCommand.CreateBrand = CatalogCommand.CreateBrand(name = name)
    }

    data class UpdateBrandRequest(
        @field:NotBlank
        val name: String,
    ) {
        fun toCommand(): CatalogCommand.UpdateBrand = CatalogCommand.UpdateBrand(name = name)
    }

    data class CreateProductRequest(
        @field:NotNull
        val brandId: Long,

        @field:NotBlank
        val name: String,

        @field:Positive
        val price: Long,

        @field:PositiveOrZero
        val initialStock: Int,

        val detailImageUrls: List<String> = emptyList(),
    ) {
        fun toCommand(): CatalogCommand.CreateProduct = CatalogCommand.CreateProduct(
            brandId = brandId,
            name = name,
            price = price,
            initialStock = initialStock,
            detailImageUrls = detailImageUrls,
        )
    }

    data class UpdateProductRequest(
        @field:NotBlank
        val name: String,

        @field:Positive
        val price: Long,

        val detailImageUrls: List<String> = emptyList(),
    ) {
        fun toCommand(): CatalogCommand.UpdateProduct = CatalogCommand.UpdateProduct(
            name = name,
            price = price,
            detailImageUrls = detailImageUrls,
        )
    }

    data class ChangeStockRequest(
        @field:Positive
        val quantity: Int,
    ) {
        fun toCommand(productId: Long): CatalogCommand.ChangeStock =
            CatalogCommand.ChangeStock(productId = productId, quantity = quantity)
    }

    data class BrandResponse(
        val brandId: Long,
        val name: String,
        val status: BrandStatus,
    ) {
        companion object {
            fun from(info: CatalogInfo.BrandInfo) = BrandResponse(info.brandId, info.name, info.status)
        }
    }

    data class BrandDetailResponse(
        val brandId: Long,
        val name: String,
        val status: BrandStatus,
        val products: List<ProductDisplayResponse>,
    ) {
        companion object {
            fun from(
                brand: CatalogInfo.BrandInfo,
                products: List<CatalogInfo.ProductDisplayInfo>,
            ) = BrandDetailResponse(
                brandId = brand.brandId,
                name = brand.name,
                status = brand.status,
                products = products.map(ProductDisplayResponse::from),
            )
        }
    }

    data class ProductResponse(
        val productId: Long,
        val brandId: Long,
        val name: String,
        val price: Long,
        val status: ProductStatus,
    ) {
        companion object {
            fun from(info: CatalogInfo.ProductInfo) =
                ProductResponse(info.productId, info.brandId, info.name, info.price, info.status)
        }
    }

    data class ProductDisplayResponse(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Long,
        val likeCount: Long,
        val likedByMe: Boolean,
        val soldOut: Boolean,
    ) {
        companion object {
            fun from(info: CatalogInfo.ProductDisplayInfo) = ProductDisplayResponse(
                productId = info.productId,
                productName = info.productName,
                brandId = info.brandId,
                brandName = info.brandName,
                price = info.price,
                likeCount = info.likeCount,
                likedByMe = info.likedByMe,
                soldOut = info.soldOut,
            )
        }
    }

    data class ProductDetailResponse(
        val product: ProductDisplayResponse,
        val detailImages: List<String>,
    ) {
        companion object {
            fun from(info: CatalogInfo.ProductDetailInfo) =
                ProductDetailResponse(ProductDisplayResponse.from(info.product), info.detailImages)
        }
    }
}

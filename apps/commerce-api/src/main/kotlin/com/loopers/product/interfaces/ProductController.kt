package com.loopers.product.interfaces

import com.loopers.product.application.ProductDetailInfo
import com.loopers.product.application.ProductInfo
import com.loopers.product.application.ProductService
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductSort
import com.loopers.shared.domain.CursorPage
import com.loopers.support.error.BadRequestException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productService: ProductService,
) {
    @GetMapping
    fun getProducts(
        @RequestParam(required = false, defaultValue = "LATEST") sort: ProductSort,
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ProductListResponse {
        if (size !in 1..MAX_PAGE_SIZE) {
            throw BadRequestException(ProductErrorCode.INVALID_PAGE_SIZE)
        }
        val page = productService.list(sort, brandId, ProductCursorCodec.decode(sort, cursor), size)
        return ProductListResponse.from(sort, page)
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ProductDetailResponse =
        ProductDetailResponse.from(productService.getDetail(productId))

    private companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}

data class ProductListItemResponse(
    val id: Long,
    val brandId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun from(info: ProductInfo): ProductListItemResponse =
            ProductListItemResponse(
                id = info.id,
                brandId = info.brandId,
                name = info.name,
                price = info.price,
                likeCount = info.likeCount,
            )
    }
}

data class ProductListResponse(
    val content: List<ProductListItemResponse>,
    val hasNext: Boolean,
    val nextCursor: String?,
) {
    companion object {
        fun from(sort: ProductSort, page: CursorPage<ProductInfo>): ProductListResponse =
            ProductListResponse(
                content = page.content.map(ProductListItemResponse::from),
                hasNext = page.hasNext,
                nextCursor = ProductCursorCodec.encode(sort, page.nextCursor),
            )
    }
}

data class ProductDetailResponse(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun from(info: ProductDetailInfo): ProductDetailResponse =
            ProductDetailResponse(
                id = info.id,
                brandId = info.brandId,
                brandName = info.brandName,
                name = info.name,
                price = info.price,
                likeCount = info.likeCount,
            )
    }
}

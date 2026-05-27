package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductCatalogDomainService {
    fun getDetail(product: ProductModel, brand: BrandModel): ProductDetail {
        if (product.brandId != brand.id) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품과 브랜드의 관계가 올바르지 않습니다.")
        }
        if (product.isDeleted() || brand.isDeleted()) {
            throw CoreException(ErrorType.NOT_FOUND, "상품 또는 브랜드를 찾을 수 없습니다.")
        }
        return ProductDetail(product = product, brand = brand)
    }

    fun getDetails(products: List<ProductModel>, brandsById: Map<Long, BrandModel>): List<ProductDetail> {
        return products.map { product ->
            val brand = brandsById[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            getDetail(product = product, brand = brand)
        }
    }
}

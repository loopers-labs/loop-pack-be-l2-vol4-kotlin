package com.loopers.application.brand

import com.loopers.application.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 브랜드 삭제 cascade 오케스트레이션.
 *
 * "브랜드 삭제"와 "그 브랜드 Product 일괄 soft delete"는 서로 다른 도메인의 독립 use case이고,
 * application service끼리 직접 호출은 금지이므로 두 use case 조합은 Facade에 둔다.
 */
@Component
class BrandFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        productService.softDeleteByBrand(id)
    }
}

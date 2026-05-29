package com.loopers.application.brand

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
    // TODO(Product 머지 후): private val productService: ProductService 주입
) {
    @Transactional
    fun delete(id: Long) {
        brandService.delete(id)
        // TODO(Product 머지 후): productService.softDeleteByBrand(id) — 브랜드 삭제 cascade soft delete (04 플랜 기능 7).
        // 옆 Product 세션에서 ProductService 주입 + 이 줄만 구현하면 됨.
    }
}

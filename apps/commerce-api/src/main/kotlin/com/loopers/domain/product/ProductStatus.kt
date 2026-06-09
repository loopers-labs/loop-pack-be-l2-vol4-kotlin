package com.loopers.domain.product

/**
 * 상품 lifecycle 상태.
 *
 * 상태가 늘어나면 [ALLOWED] 전이표만 확장한다(전이 규칙의 단일 출처).
 * DELETED 는 종료 상태이며, soft delete 는 deletedAt(null 여부)이 아니라 이 status 로 판단한다.
 */
enum class ProductStatus {
    ACTIVE,
    DELETED,
    ;

    fun canTransitionTo(target: ProductStatus): Boolean =
        target in ALLOWED.getValue(this)

    companion object {
        private val ALLOWED: Map<ProductStatus, Set<ProductStatus>> =
            mapOf(
                ACTIVE to setOf(DELETED),
                DELETED to emptySet(),
            )
    }
}

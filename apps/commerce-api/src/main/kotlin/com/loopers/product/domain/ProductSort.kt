package com.loopers.product.domain

/**
 * 상품 목록 정렬 화이트리스트. 비유니크 1차 키는 항상 `id DESC`로 타이브레이크한다.
 */
enum class ProductSort {
    LATEST, // id DESC
    PRICE_ASC, // price ASC, id DESC
    LIKES_DESC, // likeCount DESC, id DESC
}

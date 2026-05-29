package com.loopers.domain.product

import com.loopers.domain.shared.Cursor

/**
 * 상품 목록 정렬별 keyset 커서. 최종 타이브레이크 키는 항상 [id]다.
 * 최신순(LATEST)은 공용 [com.loopers.domain.shared.IdCursor]를 재사용한다.
 */

/** 가격 오름차순(price ASC, id DESC) 정렬용 복합 커서. */
data class PriceCursor(
    val price: Long,
    val id: Long,
) : Cursor

/** 좋아요 수 내림차순(likeCount DESC, id DESC) 정렬용 복합 커서. */
data class LikeCountCursor(
    val likeCount: Long,
    val id: Long,
) : Cursor

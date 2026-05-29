package com.loopers.domain.shared

/**
 * 최신순(id DESC) 정렬용 단일 키 커서. id 가 유니크 total-order라 단독으로 keyset 성립.
 * latest 정렬을 쓰는 여러 도메인이 공용으로 재사용한다.
 */
data class IdCursor(
    val id: Long,
) : Cursor

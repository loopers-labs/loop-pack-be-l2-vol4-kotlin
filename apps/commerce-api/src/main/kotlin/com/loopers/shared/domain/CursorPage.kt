package com.loopers.shared.domain

/**
 * cursor 기반 keyset 페이지네이션 공통 결과 래퍼.
 *
 * [nextCursor]는 다음 페이지 시작 [Cursor](정렬별 구현체). 다음 페이지가 없으면 null.
 * opaque base64 인코딩은 presentation(controller) 책임 — 여기서는 타입 있는 커서만 노출한다.
 */
data class CursorPage<T>(
    val content: List<T>,
    val hasNext: Boolean,
    val nextCursor: Cursor? = null,
)

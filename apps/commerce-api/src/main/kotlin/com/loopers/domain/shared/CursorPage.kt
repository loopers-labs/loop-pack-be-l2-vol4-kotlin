package com.loopers.domain.shared

/**
 * cursor 기반 keyset 페이지네이션의 결과 래퍼.
 *
 * 다음 커서는 presentation 레이어가 [content]의 마지막 요소 키셋 값에서 파생한다.
 * opaque base64 커서 인코딩은 컨트롤러 책임이므로 여기에 두지 않는다.
 */
data class CursorPage<T>(
    val content: List<T>,
    val hasNext: Boolean,
)

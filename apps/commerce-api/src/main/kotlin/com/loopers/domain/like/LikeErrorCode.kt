package com.loopers.domain.like

import com.loopers.support.error.ErrorCode

enum class LikeErrorCode(
    override val message: String,
) : ErrorCode {
    FORBIDDEN_LIKE_ACCESS("본인의 좋아요 목록만 조회할 수 있습니다."),
    ;

    override val code: String
        get() = "LIKE:$name"
}

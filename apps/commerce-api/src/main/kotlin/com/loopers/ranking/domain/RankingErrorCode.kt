package com.loopers.ranking.domain

import com.loopers.support.error.ErrorCode

enum class RankingErrorCode(override val message: String) : ErrorCode {
    INVALID_PAGE_SIZE("페이지 크기는 1 이상 100 이하여야 합니다."),
    INVALID_PAGE("페이지 번호는 1 이상이어야 합니다."),
    INVALID_DATE_FORMAT("날짜는 yyyyMMdd 형식이어야 합니다."),
    ;

    override val code: String = "RANKING:$name"
}

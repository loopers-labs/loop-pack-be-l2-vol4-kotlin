package com.loopers.support.paging

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class PageCondition(
    val page: Int = 0,
    val size: Int = 20,
) {
    init {
        if (page < 0) throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
        if (size !in 1..100) throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 100 이하이어야 합니다.")
    }
}

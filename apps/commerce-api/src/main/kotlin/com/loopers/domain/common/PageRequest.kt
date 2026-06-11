package com.loopers.domain.common

data class PageRequest(
    val page: Int = 0,
    val size: Int = 20,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in 1..MAX_SIZE) { "size는 1~$MAX_SIZE 사이여야 합니다." }
    }

    companion object {
        const val MAX_SIZE = 100
    }
}

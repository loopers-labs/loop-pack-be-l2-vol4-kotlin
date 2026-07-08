package com.loopers.domain.queue

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.util.UUID

@JvmInline
value class EntryToken(val value: String) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "입장 토큰은 비어 있을 수 없습니다.")
        }
    }

    companion object {
        fun issue(): EntryToken = EntryToken(UUID.randomUUID().toString())
    }
}

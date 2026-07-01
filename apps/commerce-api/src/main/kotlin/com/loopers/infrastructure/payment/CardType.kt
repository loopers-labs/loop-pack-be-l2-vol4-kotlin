package com.loopers.infrastructure.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 결제 카드 종류의 영속/외부연동 표현. 도메인은 카드 종류를 String 으로 다루고,
 * 유효값 검증과 변환 책임은 이 어댑터 레이어 enum 이 가진다.
 */
enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
    ;

    fun toDomain(): String = name

    companion object {
        fun from(value: String): CardType =
            runCatching { valueOf(value.uppercase()) }
                .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 카드 종류입니다: $value") }
    }
}

package com.loopers.domain.shared

import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 화폐 금액 shared kernel VO. Product 가격·Order 금액에서 재사용한다.
 * 임베딩 엔티티가 [Money]를 여러 컬럼으로 매핑할 때는 `@AttributeOverride`로 컬럼명을 지정한다.
 */
@Embeddable
data class Money(
    @Column(name = "amount", nullable = false)
    val amount: Long,
) {
    init {
        if (amount < 0) {
            throw BadRequestException(MoneyErrorCode.INVALID_MONEY)
        }
    }
}

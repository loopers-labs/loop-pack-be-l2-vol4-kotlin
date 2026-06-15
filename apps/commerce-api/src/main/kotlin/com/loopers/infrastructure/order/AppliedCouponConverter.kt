package com.loopers.infrastructure.order

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.order.AppliedCoupon
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * [AppliedCoupon] 스냅샷을 JSON 문자열 컬럼(coupon_snapshot)으로 직렬화/역직렬화한다.
 * JPA 가 인스턴스를 생성하므로 Spring 빈 주입에 의존하지 않고 전용 ObjectMapper 를 둔다.
 * (AppliedCoupon 은 Long/String 필드만 가져 JSR310 등 추가 모듈이 필요 없다.)
 */
@Converter
class AppliedCouponConverter : AttributeConverter<AppliedCoupon?, String?> {
    override fun convertToDatabaseColumn(attribute: AppliedCoupon?): String? =
        attribute?.let { OBJECT_MAPPER.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): AppliedCoupon? =
        dbData?.takeIf { it.isNotBlank() }?.let { OBJECT_MAPPER.readValue(it) }

    companion object {
        private val OBJECT_MAPPER = jacksonObjectMapper()
    }
}

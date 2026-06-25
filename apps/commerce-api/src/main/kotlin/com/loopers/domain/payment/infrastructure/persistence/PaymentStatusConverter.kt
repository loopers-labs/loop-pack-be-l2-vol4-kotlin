package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.payment.model.PaymentStatus
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = false)
class PaymentStatusConverter : AttributeConverter<PaymentStatus, Short> {
    override fun convertToDatabaseColumn(attribute: PaymentStatus?): Short? = attribute?.code

    override fun convertToEntityAttribute(dbData: Short?): PaymentStatus? =
        dbData?.let { PaymentStatus.fromCode(it) }
}

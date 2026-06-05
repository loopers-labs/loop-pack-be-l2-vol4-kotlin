package com.loopers.domain.brand.model

import com.loopers.domain.brand.exception.BrandNotActiveException
import com.loopers.domain.brand.vo.BrandName
import java.time.ZonedDateTime

data class BrandModel(
    val id: Long = 0,
    val name: BrandName,
    val deletedAtOrNull: ZonedDateTime? = null,
) {
    fun rename(name: BrandName): BrandModel = copy(name = name)

    fun delete(): BrandModel {
        if (deletedAtOrNull != null) {
            return this
        }
        return copy(deletedAtOrNull = ZonedDateTime.now())
    }

    fun requireActive() {
        if (deletedAtOrNull != null) {
            throw BrandNotActiveException(id)
        }
    }
}

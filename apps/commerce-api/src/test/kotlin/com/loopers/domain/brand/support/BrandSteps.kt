package com.loopers.domain.brand.support

import com.loopers.domain.brand.application.command.BrandRegisterCommand
import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.vo.BrandName
import java.time.ZonedDateTime

class BrandSteps {
    companion object {
        const val 기본_브랜드_ID: Long = 10L
        const val 기본_브랜드명: String = "기본 브랜드"

        fun 브랜드_도메인_생성(
            id: Long = 기본_브랜드_ID,
            name: String = 기본_브랜드명,
            deletedAtOrNull: ZonedDateTime? = null,
        ): BrandModel = BrandModel(
            id = id,
            name = BrandName.of(name),
            deletedAtOrNull = deletedAtOrNull,
        )

        fun 브랜드_등록_커맨드(
            name: String = 기본_브랜드명,
        ): BrandRegisterCommand = BrandRegisterCommand(
            name = name,
        )
    }
}

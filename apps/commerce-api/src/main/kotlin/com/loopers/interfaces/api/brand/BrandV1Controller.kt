package com.loopers.interfaces.api.brand

import com.loopers.application.brand.usecase.GetBrandUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(
    private val getBrandUsecase: GetBrandUsecase,
) {
    @GetMapping("/{brandId}")
    fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        return getBrandUsecase.execute(brandId)
            .let { BrandV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}

package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandController(
    private val brandApplicationService: BrandApplicationServicePort,
) {
    @GetMapping("/{id}")
    fun getBrand(
        @PathVariable id: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        val brand = brandApplicationService.getBrand(id)
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand))
    }
}

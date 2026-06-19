package com.loopers.domain.product.presentation

import com.loopers.domain.product.application.ProductSeeder
import com.loopers.interfaces.api.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬 전용 개발 도구 엔드포인트. 읽기 최적화 실습용 대량 데이터를 적재한다.
 * local 프로파일에서만 빈으로 등록되어 운영 환경에는 노출되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/dev/seed")
@Profile("local")
class DevSeedController(
    private val productSeeder: ProductSeeder,
) {
    @PostMapping("/products")
    fun seedProducts(
        @RequestParam(defaultValue = "100000") count: Int,
        @RequestParam(defaultValue = "100") brandCount: Int,
    ): ApiResponse<ProductSeeder.SeedResult> =
        ApiResponse.success(productSeeder.seedProducts(count, brandCount))
}

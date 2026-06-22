package com.loopers.domain.like.presentation

import com.loopers.domain.like.application.LikeSeeder
import com.loopers.interfaces.api.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬 전용 개발 도구 엔드포인트. 읽기 최적화 실습용 좋아요 분포 데이터를 적재한다.
 * local 프로파일에서만 빈으로 등록되어 운영 환경에는 노출되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/dev/seed")
@Profile("local")
class LikeSeedController(
    private val likeSeeder: LikeSeeder,
) {
    @PostMapping("/likes")
    fun seedLikes(
        @RequestParam(defaultValue = "100") maxLikesPerProduct: Int,
    ): ApiResponse<LikeSeeder.SeedResult> =
        ApiResponse.success(likeSeeder.seedLikes(maxLikesPerProduct))
}

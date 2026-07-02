package com.loopers.domain.like.presentation

import com.loopers.domain.like.application.service.LikeCountReconciliationResult
import com.loopers.domain.like.application.service.LikeCountReconciliationService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dev")
@Profile("local")
class LikeCountReconciliationController(
    private val likeCountReconciliationService: LikeCountReconciliationService,
) {
    @PostMapping("/rebuild-like-counts")
    fun rebuildLikeCounts(): ApiResponse<LikeCountReconciliationResult> =
        ApiResponse.success(likeCountReconciliationService.rebuildFromLikes())
}

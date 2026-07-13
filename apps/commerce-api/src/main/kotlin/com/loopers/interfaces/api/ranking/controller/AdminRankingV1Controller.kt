package com.loopers.interfaces.api.ranking.controller

import com.loopers.application.ranking.RankingPolicyService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.ranking.AdminRankingV1ApiSpec
import com.loopers.interfaces.api.ranking.dto.AdminRankingV1Dto
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/rankings")
class AdminRankingV1Controller(
    private val rankingPolicyService: RankingPolicyService,
) : AdminRankingV1ApiSpec {
    @PutMapping("/weights")
    override fun updateWeights(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestBody request: AdminRankingV1Dto.UpdateWeightsRequest,
    ): ApiResponse<AdminRankingV1Dto.RankingWeightsResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return rankingPolicyService.updateTodayWeights(request.toCommand())
            .let(AdminRankingV1Dto.RankingWeightsResponse::from)
            .let(ApiResponse<AdminRankingV1Dto.RankingWeightsResponse>::success)
    }
}

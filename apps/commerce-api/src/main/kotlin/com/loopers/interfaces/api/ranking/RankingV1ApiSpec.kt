package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "상품 랭킹 조회 API")
interface RankingV1ApiSpec {
    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 날짜(미지정 시 오늘, Asia/Seoul)의 랭킹판을 점수 내림차순으로 페이지 단위 조회합니다. " +
            "상품 ID 만이 아니라 이름·가격·브랜드·좋아요 수가 조립돼 반환됩니다. 인증이 필요하지 않습니다. " +
            "date 형식이 yyyyMMdd 가 아니면 400 입니다.",
    )
    fun getRankings(
        date: String?,
        page: Int,
        size: Int,
    ): ApiResponse<RankingV1Dto.RankingsResponse>
}

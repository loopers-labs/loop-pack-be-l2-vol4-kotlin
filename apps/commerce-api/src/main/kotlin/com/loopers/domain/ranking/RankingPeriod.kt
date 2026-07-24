package com.loopers.domain.ranking

enum class RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    companion object {
        fun from(value: String?): RankingPeriod =
            when (value?.uppercase()) {
                "WEEKLY" -> WEEKLY
                "MONTHLY" -> MONTHLY
                else -> DAILY
            }
    }
}

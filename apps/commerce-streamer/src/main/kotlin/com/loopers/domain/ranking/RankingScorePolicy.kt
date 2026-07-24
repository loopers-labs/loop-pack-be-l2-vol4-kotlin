package com.loopers.domain.ranking

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import kotlin.math.log10

@Component
class RankingScorePolicy(
    @Value("\${ranking.weight.view:0.1}") private val viewWeight: Double,
    @Value("\${ranking.weight.like:0.2}") private val likeWeight: Double,
    @Value("\${ranking.weight.order:0.6}") private val orderWeight: Double,
) {
    fun viewed(): Double = viewWeight

    fun likeAdded(): Double = likeWeight

    fun likeRemoved(): Double = -likeWeight

    // 금액은 log 스케일로 정규화 — 원금액 합산 시 주문이 조회/좋아요 신호를 지배하는 문제 방지(스펙 D3)
    fun ordered(unitPrice: BigDecimal, quantity: Int): Double =
        orderWeight * log10(1.0 + unitPrice.toDouble() * quantity)
}

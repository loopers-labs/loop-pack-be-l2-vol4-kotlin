package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingKeyResolver
import com.loopers.domain.ranking.RankingRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class RankingService(
    private val mapper: RankingEventMapper,
    private val keyResolver: RankingKeyResolver,
    private val rankingRepository: RankingRepository,
) {
    private val log = LoggerFactory.getLogger(RankingService::class.java)

    fun apply(jsons: List<String>) {
        val entries = jsons.mapNotNull { json ->
            runCatching { mapper.toEntry(json) }
                .getOrElse {
                    log.warn("Failed to parse ranking event, skipping.", it)
                    null
                }
        }
        if (entries.isEmpty()) return
        try {
            rankingRepository.applyAll(entries, keyResolver.windowFor(ZonedDateTime.now()))
        } catch (e: DataAccessException) {
            // 랭킹은 근사 집계 — Redis 장애 시 배치 스킵(경보 로그), 컨슈머는 생존(스펙 §7)
            log.error("Failed to apply ranking batch, skipping. size={}", entries.size, e)
        }
    }
}

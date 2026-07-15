package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * 이월(carry-over) 상태 조회·복구용 아웃바운드 포트. 상태 키는 정기 이월 배치(commerce-batch)와 공유해
 * 배치 실행 중 api 측 복구가 중복 진입하지 못하게 한다 (PROGRESS SET NX = 분산 락).
 */
interface RankingRolloverPort {
    /** targetDate 보드의 이월 상태. DONE이 아닌 한 오늘 보드는 서빙하지 않는다. */
    fun getStatus(targetDate: LocalDate): RankingRolloverStatus

    /** PROGRESS 선점 시도(SET NX). 성공 = 이월 실행 권한 획득. 이미 다른 주체가 실행 중이면 false. */
    fun tryStart(targetDate: LocalDate): Boolean

    /**
     * snapshot:{fromDate}의 각 점수를 floor(×0.1)해 all/snapshot:{toDate}에 반영한다. 결과 0점은 제외.
     * 페이지 순회마다 PROGRESS TTL을 갱신(heartbeat)해 장기 실행에도 상태가 유지되게 한다.
     */
    fun carryOverSnapshot(fromDate: LocalDate, toDate: LocalDate)

    /** 이월 완료 표시 — status를 DONE으로 덮어쓴다. 이후 요청부터 정상 경로. */
    fun complete(targetDate: LocalDate)

    /** 이월 미완료 WARN 로그 중복 방지 가드(SET NX). 최초 관측이면 true — 그때만 로깅한다. */
    fun tryMarkNotified(targetDate: LocalDate): Boolean
}

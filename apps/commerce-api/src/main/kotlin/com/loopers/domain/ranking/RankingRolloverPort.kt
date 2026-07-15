package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * 이월(carry-over) 복구용 아웃바운드 포트. 정기 이월 배치(commerce-batch)가 실패해 오늘 보드가 비어 있을 때,
 * api가 분산 락을 잡고 직접 복구를 실행한다 (락 키는 배치와 공유).
 */
interface RankingRolloverPort {
    /** targetDate 보드에 대한 이월 실행 락 획득 시도. 이미 다른 주체가 잡고 있으면 false. */
    fun tryLock(targetDate: LocalDate): Boolean

    fun releaseLock(targetDate: LocalDate)

    /** snapshot:{fromDate}의 각 점수를 floor(×0.1)해 all/snapshot:{toDate}에 반영한다. 결과 0점은 제외. */
    fun carryOverSnapshot(fromDate: LocalDate, toDate: LocalDate)
}

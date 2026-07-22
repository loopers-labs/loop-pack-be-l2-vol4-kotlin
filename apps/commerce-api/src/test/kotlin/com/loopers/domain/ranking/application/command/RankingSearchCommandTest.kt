package com.loopers.domain.ranking.application.command

import com.loopers.domain.ranking.vo.RankingKey
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RankingSearchCommandTest {
    @Test
    fun `랭킹_검색조건은_기본값으로_서울_오늘_첫페이지_20개를_사용한다`() {
        val command = RankingSearchCommand.of()

        assertThat(command.date).isEqualTo(LocalDate.now(RankingKey.ZONE))
        assertThat(command.page).isEqualTo(0)
        assertThat(command.size).isEqualTo(20)
    }

    @Test
    fun `날짜는_yyyyMMdd_형식으로_파싱한다`() {
        val command = RankingSearchCommand.of(date = "20260717")

        assertThat(command.date).isEqualTo(LocalDate.of(2026, 7, 17))
    }

    @Test
    fun `잘못된_날짜_형식은_BAD_REQUEST가_발생한다`() {
        val ex = assertThrows<CoreException> {
            RankingSearchCommand.of(date = "2026-07-17")
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `페이지_번호는_0_이상이어야_한다`() {
        val ex = assertThrows<CoreException> {
            RankingSearchCommand.of(page = -1)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `페이지_크기는_1_이상이어야_한다`() {
        val ex = assertThrows<CoreException> {
            RankingSearchCommand.of(size = 0)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}

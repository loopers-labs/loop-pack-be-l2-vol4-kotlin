package com.loopers.domain.waitingqueue.unit

import com.loopers.domain.waitingqueue.application.service.WaitingQueueService
import com.loopers.domain.waitingqueue.model.AdmissionTokenCandidate
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.domain.waitingqueue.port.WaitingQueuePort
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.기본_멱등키
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.기본_사용자_ID
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.기본_토큰
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.기준_시각
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.대기열_설정
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.대기열_항목
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.대기중_상태
import com.loopers.domain.waitingqueue.support.WaitingQueueSteps.Companion.입장_상태
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

@ExtendWith(OutputCaptureExtension::class)
class WaitingQueueServiceTest {
    @Test
    fun `이미_대기중인_사용자는_중복_등록되지_않는다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        val existing = 대기중_상태(position = 7L, totalWaiting = 10L)
        every { port.findState(기본_사용자_ID, 기준_시각) } returns existing

        val result = service.enter(기본_사용자_ID)

        assertThat(result.status.name).isEqualTo("WAITING")
        assertThat(result.position).isEqualTo(7L)
        assertThat(result.totalWaiting).isEqualTo(10L)
        verify(exactly = 0) { port.enqueueIfAbsent(any(), any()) }
    }

    @Test
    fun `이미_입장_토큰이_있는_사용자는_대기열에_다시_들어가지_않는다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        val existing = 입장_상태()
        every { port.findState(기본_사용자_ID, 기준_시각) } returns existing

        val result = service.enter(기본_사용자_ID)

        assertThat(result.status.name).isEqualTo("ADMITTED")
        assertThat(result.token).isEqualTo(기본_토큰)
        assertThat(result.position).isNull()
        verify(exactly = 0) { port.enqueueIfAbsent(any(), any()) }
    }

    @Test
    fun `대기열_진입은_필수_단조_sequence를_발급한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        val entry = 대기열_항목(sequence = 42L)
        val state = 대기중_상태(entry = entry, position = 1L, totalWaiting = 1L)
        every { port.findState(기본_사용자_ID, 기준_시각) } returns null andThen state
        every { port.enqueueIfAbsent(기본_사용자_ID, 기준_시각) } returns entry

        val result = service.enter(기본_사용자_ID)

        assertThat(result.sequence).isEqualTo(42L)
        assertThat(result.sequence).isPositive()
        verifySequence {
            port.findState(기본_사용자_ID, 기준_시각)
            port.enqueueIfAbsent(기본_사용자_ID, 기준_시각)
            port.findState(기본_사용자_ID, 기준_시각)
        }
    }

    @Test
    fun `입장_토큰은_기본_5분_TTL을_가진다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port, tokenTtl = Duration.ofMinutes(5), admissionBatchSize = 1)
        val entry = 대기열_항목()
        val candidatesSlot = slot<List<AdmissionTokenCandidate>>()
        every {
            port.admitNext(capture(candidatesSlot), Duration.ofMinutes(5), 기준_시각)
        } returns listOf(entry)

        service.admitBatch()

        assertThat(candidatesSlot.captured.single().availableAt).isEqualTo(기준_시각)
        assertThat(candidatesSlot.captured.single().expiresAt)
            .isEqualTo(기준_시각.plus(Duration.ofMinutes(5)))
    }

    @Test
    fun `입장_스케줄러는_sequence가_낮은_사용자부터_batch만큼_입장시키고_jitter_availableAt을_부여한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port, admissionBatchSize = 3)
        val entries = listOf(
            대기열_항목(userId = 1L, sequence = 11L),
            대기열_항목(userId = 2L, sequence = 12L),
            대기열_항목(userId = 3L, sequence = 13L),
        )
        val candidatesSlot = slot<List<AdmissionTokenCandidate>>()
        every { port.admitNext(capture(candidatesSlot), any(), 기준_시각) } returns entries

        val result = service.admitBatch()

        assertThat(result.admittedCount).isEqualTo(3)
        assertThat(result.admittedUserIds).containsExactly(1L, 2L, 3L)
        assertThat(candidatesSlot.captured.map { it.availableAt }).containsExactly(
            기준_시각,
            기준_시각.plusSeconds(1),
            기준_시각.plusSeconds(2),
        )
    }

    @Test
    fun `기본_지터는_입장_시각을_batch_내_균등_분산한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = WaitingQueueService(
            port,
            대기열_설정(schedulerJitterMax = Duration.ofSeconds(1), admissionBatchSize = 4),
        )
        val entries = (1L..4L).map { 대기열_항목(userId = it, sequence = it + 10L) }
        val candidatesSlot = slot<List<AdmissionTokenCandidate>>()
        every { port.admitNext(capture(candidatesSlot), any(), any()) } returns entries

        service.admitBatch()

        val availableAtValues = candidatesSlot.captured.map { it.availableAt }
        val offsets = availableAtValues.map { Duration.between(availableAtValues.first(), it).toMillis() }
        assertThat(offsets).containsExactly(0L, 250L, 500L, 750L)
    }

    @Test
    fun `주문_생성_성공_후에만_토큰을_소모한다`() {
        val port = mockk<WaitingQueuePort>(relaxed = true)
        val service = service(port)
        every { port.consumeToken(기본_사용자_ID, 기본_토큰, 기본_멱등키) } returns true

        service.consumeAfterOrderCreated(기본_사용자_ID, 기본_토큰, 기본_멱등키)

        verify(exactly = 1) { port.consumeToken(기본_사용자_ID, 기본_토큰, 기본_멱등키) }
    }

    @Test
    fun `주문_성공_후_토큰_소모_CAS가_실패하면_SERVICE_UNAVAILABLE이_발생한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every { port.consumeToken(기본_사용자_ID, 기본_토큰, 기본_멱등키) } returns false

        assertServiceUnavailable {
            service.consumeAfterOrderCreated(기본_사용자_ID, 기본_토큰, 기본_멱등키)
        }
    }

    @Test
    fun `주문_실패_후_토큰_해제_CAS가_실패하면_SERVICE_UNAVAILABLE이_발생한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every { port.releaseToken(기본_사용자_ID, 기본_토큰, 기본_멱등키) } returns false

        assertServiceUnavailable {
            service.releaseAfterOrderFailed(기본_사용자_ID, 기본_토큰, 기본_멱등키)
        }
    }

    @Test
    fun `성공후_소모된_토큰은_동일_멱등키_재시도만_허용한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every {
            port.validateToken(기본_사용자_ID, 기본_토큰, 기본_멱등키, 기준_시각)
        } returns TokenValidationResult.consumedBySameIdempotencyKey(기본_멱등키)
        every {
            port.validateToken(기본_사용자_ID, 기본_토큰, "other-key", 기준_시각)
        } returns TokenValidationResult.consumedByDifferentIdempotencyKey(기본_멱등키)

        service.validateForOrder(기본_사용자_ID, 기본_토큰, 기본_멱등키)
        val ex = assertThrows<CoreException> {
            service.validateForOrder(기본_사용자_ID, 기본_토큰, "other-key")
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
    }

    @Test
    fun `대기열_진입에서_저장소_상태를_판단할_수_없으면_SERVICE_UNAVAILABLE이_발생한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every { port.findState(기본_사용자_ID, 기준_시각) } throws
            DataAccessResourceFailureException("waiting queue store unavailable")

        assertServiceUnavailable {
            service.enter(기본_사용자_ID)
        }
    }

    @Test
    fun `순번_조회에서_저장소_상태를_판단할_수_없으면_SERVICE_UNAVAILABLE이_발생한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every { port.findState(기본_사용자_ID, 기준_시각) } throws
            DataAccessResourceFailureException("waiting queue store unavailable")

        assertServiceUnavailable {
            service.position(기본_사용자_ID)
        }
    }

    @Test
    fun `순번_조회에서_대기열_진입_이력이_없으면_NOT_FOUND를_로그와_함께_반환한다`(output: CapturedOutput) {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every { port.findState(기본_사용자_ID, 기준_시각) } returns null

        val ex = assertThrows<CoreException> {
            service.position(기본_사용자_ID)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(ex.customMessage).isEqualTo("대기열에서 사용자를 찾을 수 없습니다.")
        assertThat(output.all)
            .contains("Waiting queue entry was not found")
            .contains("userId=$기본_사용자_ID")
    }

    @Test
    fun `주문_관문에서_토큰_저장소를_판단할_수_없으면_SERVICE_UNAVAILABLE이_발생한다`() {
        val port = mockk<WaitingQueuePort>()
        val service = service(port)
        every {
            port.validateToken(기본_사용자_ID, 기본_토큰, 기본_멱등키, 기준_시각)
        } throws DataAccessResourceFailureException("waiting queue store unavailable")

        assertServiceUnavailable {
            service.validateForOrder(기본_사용자_ID, 기본_토큰, 기본_멱등키)
        }
    }

    private fun assertServiceUnavailable(action: () -> Any?) {
        val ex = assertThrows<CoreException> {
            action()
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE)
    }

    private fun service(
        port: WaitingQueuePort,
        tokenTtl: Duration = Duration.ofMinutes(5),
        admissionBatchSize: Int = 3,
    ): WaitingQueueService {
        val tokens = ArrayDeque(listOf("q_token_1", "q_token_2", "q_token_3"))
        return WaitingQueueService(
            port,
            대기열_설정(tokenTtl = tokenTtl, admissionBatchSize = admissionBatchSize),
            Clock.fixed(기준_시각, ZoneOffset.UTC),
            { tokens.removeFirst() },
            { index -> Duration.ofSeconds(index.toLong()) },
        )
    }
}

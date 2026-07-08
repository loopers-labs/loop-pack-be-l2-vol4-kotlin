package com.loopers.domain.queue

import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

// 활성화 스케줄러가 테스트 중간에 개입해 Waiting 이 Ready 로 바뀌지 않도록 주기를 사실상 무한대로 늘린다.
@SpringBootTest(properties = ["queue.activate.interval=1h"])
class WaitingQueueServiceIntegrationTest @Autowired constructor(
    private val waitingQueueService: WaitingQueueService,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() = redisCleanUp.truncateAll()

    @Test
    @DisplayName("대기열에 진입하면 입장 순서대로 1부터 시작하는 순번이 부여된다")
    fun enterAssignsPositionInOrder() {
        val first = waitingQueueService.enter("user-1")
        val second = waitingQueueService.enter("user-2")

        assertThat(first).isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 1))
        assertThat(second).isEqualTo(QueueStatus.Waiting(position = 2, totalWaiting = 2))
    }

    @Test
    @DisplayName("재진입해도 기존 순번이 유지된다")
    fun reEnterKeepsExistingPosition() {
        waitingQueueService.enter("user-1")
        waitingQueueService.enter("user-2")

        val reEntered = waitingQueueService.enter("user-1")

        assertThat(reEntered).isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 2))
    }

    @Test
    @DisplayName("활성화되면 앞사람부터 Ready(토큰)가 되고 대기열에서 빠지며, 남은 사람 순번이 당겨진다")
    fun activateNextIssuesTokensInOrder() {
        waitingQueueService.enter("user-1")
        waitingQueueService.enter("user-2")
        waitingQueueService.enter("user-3")

        waitingQueueService.activateNext(2)

        assertThat(waitingQueueService.status("user-1")).isInstanceOf(QueueStatus.Ready::class.java)
        assertThat(waitingQueueService.status("user-2")).isInstanceOf(QueueStatus.Ready::class.java)
        assertThat(waitingQueueService.status("user-3"))
            .isEqualTo(QueueStatus.Waiting(position = 1, totalWaiting = 1))
    }

    @Test
    @DisplayName("토큰 보유자가 재진입하면 줄을 서지 않고 기존 토큰의 Ready 를 받는다")
    fun readyUserReEnterReturnsSameToken() {
        waitingQueueService.enter("user-1")
        waitingQueueService.activateNext(1)
        val issued = waitingQueueService.status("user-1") as QueueStatus.Ready

        val reEntered = waitingQueueService.enter("user-1")

        assertThat(reEntered).isEqualTo(QueueStatus.Ready(issued.token))
    }

    @Test
    @DisplayName("대기열에 없고 토큰도 없는 유저는 NotInQueue 다")
    fun unknownUserIsNotInQueue() {
        assertThat(waitingQueueService.status("stranger")).isEqualTo(QueueStatus.NotInQueue)
    }

    @Test
    @DisplayName("대기 인원보다 큰 수로 활성화해도 있는 만큼만 처리된다")
    fun activateMoreThanWaitingIsSafe() {
        waitingQueueService.enter("user-1")

        waitingQueueService.activateNext(10)

        assertThat(waitingQueueService.status("user-1")).isInstanceOf(QueueStatus.Ready::class.java)
    }
}

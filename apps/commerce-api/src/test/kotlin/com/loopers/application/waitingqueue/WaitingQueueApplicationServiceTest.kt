package com.loopers.application.waitingqueue

import com.loopers.config.waitingqueue.WaitingQueueProperties
import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.domain.waitingqueue.WaitingQueueRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class WaitingQueueApplicationServiceTest {
    private val repository = FakeWaitingQueueRepository()
    private val properties = WaitingQueueProperties().apply {
        admitRatePerSecond = 10
    }
    private val service = WaitingQueueApplicationService(repository, properties)

    @Test
    fun verifyOrEnqueueAllowsCheckoutWhenQueueIsDisabled() {
        properties.enabled = false
        repository.enterQueue = true

        val decision = service.verifyOrEnqueue(userId = 1L, token = null)

        assertAll(
            { assertThat(decision).isEqualTo(WaitingQueueDecision.Allowed) },
            { assertThat(repository.enqueuedUserIds).isEmpty() },
        )
    }

    @Test
    fun verifyOrEnqueueReturnsPollingTokenWhenQueueIsRequired() {
        repository.enterQueue = true
        repository.nextToken = "waiting-token"

        val decision = service.verifyOrEnqueue(userId = 1L, token = null)

        assertAll(
            { assertThat(decision).isEqualTo(WaitingQueueDecision.Polling("waiting-token")) },
            { assertThat(repository.enqueuedUserIds).containsExactly(1L) },
        )
    }

    @Test
    fun verifyOrEnqueueConsumesAllowedTokenBeforeCheckout() {
        val decision = service.verifyOrEnqueue(userId = 1L, token = "allowed-token")

        assertAll(
            { assertThat(decision).isEqualTo(WaitingQueueDecision.Allowed) },
            { assertThat(repository.consumedTokens).containsExactly(1L to "allowed-token") },
        )
    }

    @Test
    fun pollReturnsDynamicIntervalAndEstimatedLeftTime() {
        repository.position = WaitingQueuePosition.Waiting(leftPeople = 25)

        val response = service.poll(userId = 1L, token = "waiting-token")

        assertAll(
            { assertThat(response.status).isEqualTo("waiting") },
            { assertThat(response.leftPeople).isEqualTo(25) },
            { assertThat(response.leftTime).isEqualTo(3) },
            { assertThat(response.nextPollIn).isEqualTo(3) },
        )
    }

    @Test
    fun pollReturnsAllowedWhenTokenWasAdmitted() {
        repository.position = WaitingQueuePosition.Allowed

        val response = service.poll(userId = 1L, token = "allowed-token")

        assertAll(
            { assertThat(response.status).isEqualTo("allowed") },
            { assertThat(response.leftPeople).isZero() },
            { assertThat(response.leftTime).isZero() },
            { assertThat(response.nextPollIn).isEqualTo(1) },
        )
    }
}

private class FakeWaitingQueueRepository : WaitingQueueRepository {
    var enterQueue: Boolean = false
    var nextToken: String = "token"
    var position: WaitingQueuePosition = WaitingQueuePosition.Waiting(leftPeople = 0)
    val enqueuedUserIds = mutableListOf<Long>()
    val consumedTokens = mutableListOf<Pair<Long, String>>()

    override fun shouldEnterQueue(): Boolean =
        enterQueue

    override fun enqueue(userId: Long): String {
        enqueuedUserIds += userId
        return nextToken
    }

    override fun getPosition(userId: Long, token: String): WaitingQueuePosition =
        position

    override fun consumeAllowedToken(userId: Long, token: String) {
        consumedTokens += userId to token
    }

    override fun isAdmissionAlive(): Boolean =
        true
}

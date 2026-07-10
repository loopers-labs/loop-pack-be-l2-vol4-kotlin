package com.loopers.queue.application

import com.loopers.queue.domain.QueueErrorCode
import com.loopers.queue.infrastructure.redis.OrderQueueRepository
import com.loopers.support.error.ConflictException
import com.loopers.support.error.ServiceUnavailableException
import com.loopers.support.error.TooManyRequestsException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotlin.math.ceil
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

@Component
class OrderQueueService(
    private val orderQueueRepository: OrderQueueRepository,
    private val properties: OrderQueueProperties,
) {
    private val logger = LoggerFactory.getLogger(OrderQueueService::class.java)

    fun enter(userId: Long): QueuePositionInfo = failClose {
        if (orderQueueRepository.findToken(userId) != null) {
            throw ConflictException(QueueErrorCode.ALREADY_ADMITTED)
        }
        if (orderQueueRepository.totalWaiting() >= properties.maxWaiting) {
            throw TooManyRequestsException(QueueErrorCode.QUEUE_FULL)
        }
        orderQueueRepository.enter(userId, System.currentTimeMillis())
        position(userId)
    }

    fun position(userId: Long): QueuePositionInfo = failClose {
        val token = orderQueueRepository.findToken(userId)
        if (token != null) {
            return@failClose QueuePositionInfo.admitted(token)
        }
        val rank = orderQueueRepository.rank(userId) ?: return@failClose QueuePositionInfo.notInQueue()
        val estimatedWaitSeconds = ceil((rank + 1) / properties.admissionsPerSecond).toLong()
        QueuePositionInfo(
            status = QueueEntryStatus.WAITING,
            position = rank + 1,
            totalWaiting = orderQueueRepository.totalWaiting(),
            estimatedWaitSeconds = estimatedWaitSeconds,
            nextPollSeconds = nextPollSeconds(estimatedWaitSeconds),
            token = null,
        )
    }

    fun verifyAdmission(userId: Long) {
        failClose {
            orderQueueRepository.findToken(userId) ?: throw ConflictException(QueueErrorCode.ENTRY_TOKEN_REQUIRED)
        }
    }

    fun completeOrder(userId: Long) {
        try {
            orderQueueRepository.deleteToken(userId)
        } catch (e: CallNotPermittedException) {
            logger.warn("입장 토큰 삭제 실패 — TTL 만료로 소멸. userId={}, cause={}", userId, e.javaClass.simpleName)
        } catch (e: DataAccessException) {
            logger.warn("입장 토큰 삭제 실패 — TTL 만료로 소멸. userId={}, cause={}", userId, e.javaClass.simpleName)
        }
    }

    private fun nextPollSeconds(estimatedWaitSeconds: Long): Long = (estimatedWaitSeconds / 10).coerceIn(1, 10)

    private inline fun <T> failClose(block: () -> T): T = try {
        block()
    } catch (e: CallNotPermittedException) {
        throw ServiceUnavailableException(QueueErrorCode.QUEUE_UNAVAILABLE)
    } catch (e: DataAccessException) {
        throw ServiceUnavailableException(QueueErrorCode.QUEUE_UNAVAILABLE)
    }
}

@ConfigurationProperties(value = "queue")
data class OrderQueueProperties(
    val scheduler: Scheduler = Scheduler(),
    val token: Token = Token(),
    val maxWaiting: Long = 60_000,
) {
    val admissionsPerSecond: Double get() = scheduler.batchSize * 1000.0 / scheduler.fixedDelay

    data class Scheduler(val batchSize: Int = 10, val fixedDelay: Long = 100)

    data class Token(val ttlSeconds: Long = 300)
}

enum class QueueEntryStatus { WAITING, ADMITTED, NOT_IN_QUEUE }

data class QueuePositionInfo(
    val status: QueueEntryStatus,
    val position: Long?,
    val totalWaiting: Long?,
    val estimatedWaitSeconds: Long?,
    val nextPollSeconds: Long?,
    val token: String?,
) {
    companion object {
        fun admitted(token: String): QueuePositionInfo =
            QueuePositionInfo(QueueEntryStatus.ADMITTED, null, null, null, null, token)

        fun notInQueue(): QueuePositionInfo =
            QueuePositionInfo(QueueEntryStatus.NOT_IN_QUEUE, null, null, null, null, null)
    }
}

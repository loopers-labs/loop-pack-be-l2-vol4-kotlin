package com.loopers.application.queue

import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@TestPropertySource(properties = ["queue.refill-per-second=50"])
@SpringBootTest
class QueuePositionEnrichTest {
    @Autowired lateinit var getPosition: GetQueuePositionUsecase

    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var userService: UserService

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("대기 중이면 예상 대기시간(rank/throughput)이 계산된다.")
    @Test
    fun estimatesWait() {
        // arrange: 유저 100명 진입 후 대상 유저 u(rank=99). 실제 회원가입 없이 큐 프리미티브로 앞자리 99명을 채운다.
        repeat(99) { i -> repository.enter(userId = 9_000_000L + i, nowMillis = 1_000L + i) }
        val u = signUp("queuePosEstU")
        val userId = userService.getProfile(u.loginId, u.rawPassword).id
        repository.enter(userId = userId, nowMillis = 1_000L + 100)

        val result = getPosition.execute(u.loginId, u.rawPassword)
        assertThat(result.waiting).isTrue()
        assertThat(result.estimatedWaitSeconds).isEqualTo(99 / 50) // ≈1s (정수)
    }

    @DisplayName("토큰이 발급되면 position=0, token 동봉.")
    @Test
    fun tokenIssued() {
        val u = signUp("queuePosTokenU")
        val userId = userService.getProfile(u.loginId, u.rawPassword).id
        repository.issueToken(userId, "tok-1", 300, 1000) // waiting에는 없음

        val result = getPosition.execute(u.loginId, u.rawPassword)
        assertThat(result.position).isEqualTo(0L)
        assertThat(result.token).isEqualTo("tok-1")
    }

    @DisplayName("Redis 장애(DataAccessException)만 degraded 응답으로 처리하고, 그 외 예외는 전파한다.")
    @Test
    fun degradesOnRedisFailureOnly() {
        val u = signUp("queuePosDegradeU")

        val redisDown = GetQueuePositionUsecase(userService, throwingRepo(RedisConnectionFailureException("down")), 50)
        val degraded = redisDown.execute(u.loginId, u.rawPassword)
        assertThat(degraded.position).isNull()
        assertThat(degraded.waiting).isFalse()

        val buggy = GetQueuePositionUsecase(userService, throwingRepo(IllegalStateException("bug")), 50)
        assertThatThrownBy { buggy.execute(u.loginId, u.rawPassword) }
            .isInstanceOf(IllegalStateException::class.java) // 코드 버그는 장애로 위장되지 않음
    }

    @DisplayName("refill rate가 0 이하로 설정되면 기동 시점에 즉시 실패한다(0으로 나누기 오진 방지).")
    @Test
    fun rejectsNonPositiveRefillRate() {
        assertThatThrownBy { GetQueuePositionUsecase(userService, repository, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun throwingRepo(e: RuntimeException) = object : OrderQueueRepository {
        override fun enter(userId: Long, nowMillis: Long): Long = throw e
        override fun rank(userId: Long): Long? = throw e
        override fun total(): Long = throw e
        override fun pruneExpiredProcessing(beforeMillis: Long) = throw e
        override fun countActive(): Long = throw e
        override fun popNext(count: Int): List<Long> = throw e
        override fun issueToken(userId: Long, token: String, ttlSeconds: Long, nowMillis: Long) = throw e
        override fun findToken(userId: Long): String? = throw e
        override fun claimToken(userId: Long, token: String): Boolean = throw e
        override fun release(userId: Long) = throw e
    }

    private fun signUp(loginId: String, rawPassword: String = PASSWORD): Account {
        userService.signUp(
            UserService.SignUpCommand(
                loginId = loginId,
                password = rawPassword,
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@loopers.com",
            ),
        )
        return Account(loginId = loginId, rawPassword = rawPassword)
    }

    private data class Account(val loginId: String, val rawPassword: String)

    companion object {
        private const val PASSWORD = "Password1!"
    }
}

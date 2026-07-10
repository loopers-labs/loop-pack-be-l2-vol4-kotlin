package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.runConcurrently
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.RedisConnectionFailureException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class EntryTokenGateIntegrationTest {
    @Autowired lateinit var gate: EntryTokenGate

    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var userService: UserService

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("발급된 토큰과 일치하면 claim이 통과(userId 반환)하며 토큰은 원자적으로 소비되고, complete로 processing이 회수된다.")
    @Test
    fun validTokenClaims() {
        val u = signUp("gateUserA")
        repository.issueToken(u.id, "tok-1", 300, 1000)

        val userId = gate.claim(u.loginId, u.rawPassword, "tok-1")
        assertThat(userId).isEqualTo(u.id)
        assertThat(repository.findToken(u.id)).isNull() // claim 시점에 소비됨

        gate.complete(u.id)
        assertThat(repository.countActive()).isEqualTo(0L)
    }

    @DisplayName("토큰이 없거나 불일치면 TOO_MANY_REQUESTS로 차단하고, 불일치 요청은 기존 유효 토큰을 파괴하지 않는다.")
    @Test
    fun invalidTokenBlocked() {
        val u = signUp("gateUserB")
        repository.issueToken(u.id, "tok-1", 300, 1000)

        assertThatThrownBy { gate.claim(u.loginId, u.rawPassword, "wrong") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.TOO_MANY_REQUESTS)
        assertThat(repository.findToken(u.id)).isEqualTo("tok-1")

        assertThatThrownBy { gate.claim(u.loginId, u.rawPassword, null) }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.TOO_MANY_REQUESTS)
    }

    @DisplayName("같은 토큰으로 동시 요청이 와도 정확히 1개만 통과한다(검증=소비 원자화, TOCTOU 차단).")
    @Test
    fun concurrentClaimsAdmitExactlyOne() {
        val u = signUp("gateUserC")
        repository.issueToken(u.id, "tok-1", 300, 1000)

        val admitted = AtomicInteger(0)
        runConcurrently(threadCount = 10) {
            try {
                gate.claim(u.loginId, u.rawPassword, "tok-1")
                admitted.incrementAndGet()
            } catch (e: CoreException) {
                // TOO_MANY_REQUESTS — 나머지 요청은 차단
            }
        }
        assertThat(admitted.get()).isEqualTo(1)
    }

    @DisplayName("주문 실패 시 restore로 같은 토큰이 복원되어 재시도할 수 있다.")
    @Test
    fun restoreEnablesRetry() {
        val u = signUp("gateUserD")
        repository.issueToken(u.id, "tok-1", 300, 1000)
        gate.claim(u.loginId, u.rawPassword, "tok-1")

        gate.restore(u.id, "tok-1")
        assertThat(repository.findToken(u.id)).isEqualTo("tok-1")
        assertThat(gate.claim(u.loginId, u.rawPassword, "tok-1")).isEqualTo(u.id)
    }

    @DisplayName("Redis 장애(DataAccessException) 시 게이트를 우회(fail-open)하고, 그 외 예외는 전파한다.")
    @Test
    fun failsOpenOnRedisFailureOnly() {
        val u = signUp("gateUserE")
        val redisDown = EntryTokenGate(userService, throwingRepo(RedisConnectionFailureException("down")), 300)
        assertThat(redisDown.claim(u.loginId, u.rawPassword, null)).isEqualTo(u.id) // 토큰 없어도 bypass

        val buggy = EntryTokenGate(userService, throwingRepo(IllegalStateException("bug")), 300)
        assertThatThrownBy { buggy.claim(u.loginId, u.rawPassword, "tok") }
            .isInstanceOf(IllegalStateException::class.java) // 코드 버그는 장애로 위장되지 않음
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
        val saved = userService.signUp(
            UserService.SignUpCommand(
                loginId = loginId,
                password = rawPassword,
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@loopers.com",
            ),
        )
        return Account(id = saved.id, loginId = loginId, rawPassword = rawPassword)
    }

    private data class Account(val id: Long, val loginId: String, val rawPassword: String)

    companion object {
        private const val PASSWORD = "Password1!"
    }
}

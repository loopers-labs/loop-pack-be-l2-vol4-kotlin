package com.loopers.application.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class EntryTokenGateIntegrationTest {
    @Autowired lateinit var gate: EntryTokenGate

    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var userService: UserService

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("발급된 토큰과 일치하면 통과(userId 반환)하고, consume하면 토큰이 사라진다.")
    @Test
    fun validTokenPasses() {
        // arrange: 유저 u 생성, 토큰 발급
        val u = signUp("gateUserA")
        repository.issueToken(u.id, "tok-1", 300, 1000)

        val userId = gate.validate(u.loginId, u.rawPassword, "tok-1")
        assertThat(userId).isEqualTo(u.id)

        gate.consume(u.id)
        assertThat(repository.findToken(u.id)).isNull()
    }

    @DisplayName("토큰이 없거나 불일치면 TOO_MANY_REQUESTS로 차단한다.")
    @Test
    fun invalidTokenBlocked() {
        // arrange: 유저 u 생성(토큰 미발급)
        val u = signUp("gateUserB")

        assertThatThrownBy { gate.validate(u.loginId, u.rawPassword, "wrong") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.TOO_MANY_REQUESTS)
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

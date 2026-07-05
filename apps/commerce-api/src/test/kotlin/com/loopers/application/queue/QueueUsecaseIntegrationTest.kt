package com.loopers.application.queue

import com.loopers.application.queue.usecase.EnterQueueUsecase
import com.loopers.application.queue.usecase.GetQueuePositionUsecase
import com.loopers.domain.user.UserService
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class QueueUsecaseIntegrationTest {
    @Autowired lateinit var enterQueue: EnterQueueUsecase

    @Autowired lateinit var getPosition: GetQueuePositionUsecase

    @Autowired lateinit var userService: UserService

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("대기열에 진입하면 순번이 부여되고, 순번 조회가 일치한다.")
    @Test
    fun enterAndPosition() {
        // arrange: 활성 유저 2명 생성 (기존 signup 방식)
        val a = signUp("queueUserA")
        val b = signUp("queueUserB")

        val posA = enterQueue.execute(a.loginId, a.rawPassword)
        val posB = enterQueue.execute(b.loginId, b.rawPassword)

        assertThat(posA).isEqualTo(1L)
        assertThat(posB).isEqualTo(2L)
        assertThat(getPosition.execute(a.loginId, a.rawPassword).position).isEqualTo(1L)
        assertThat(getPosition.execute(a.loginId, a.rawPassword).waiting).isTrue()
    }

    @DisplayName("미진입 유저의 순번 조회는 waiting=false, position=null.")
    @Test
    fun positionWhenNotEntered() {
        // arrange: 유저 c 생성 (진입 X)
        val c = signUp("queueUserC")

        val result = getPosition.execute(c.loginId, c.rawPassword)
        assertThat(result.waiting).isFalse()
        assertThat(result.position).isNull()
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

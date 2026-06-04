package com.loopers.domain.user.integration

import com.loopers.domain.user.application.UserService
import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.exception.DuplicateLoginIdException
import com.loopers.domain.user.infrastructure.persistence.UserJpaRepository
import com.loopers.domain.user.infrastructure.persistence.UserRepositoryImpl
import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.port.UserRepository
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.기본_생년월일
import com.loopers.domain.user.support.UserSteps.Companion.기본_이름
import com.loopers.domain.user.support.UserSteps.Companion.기본_이메일
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.domain.user.vo.Password
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class UserServiceIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val userJpaRepository: UserJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `유효한_커맨드면_사용자가_저장된다`() {
            val result = userService.signUp(사용자_회원가입())

            val saved = userJpaRepository.findByLoginId(기본_로그인_ID)
            assertThat(result.loginId.value).isEqualTo(기본_로그인_ID)
            assertThat(saved).isNotNull
            assertThat(saved?.email).isEqualTo(기본_이메일)
        }

        @Test
        fun `중복_로그인ID로_가입하면_CONFLICT가_발생한다`() {
            val loginId = "duplicate"
            userService.signUp(사용자_회원가입(loginId = loginId))

            val ex = assertThrows<CoreException> {
                userService.signUp(사용자_회원가입(loginId = loginId, email = "other@example.com"))
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(ex.message).doesNotContain(loginId)
            assertThat(ex.cause).isInstanceOf(DuplicateLoginIdException::class.java)
        }

        @Test
        fun `도메인_제약을_위반하면_BAD_REQUEST가_발생한다`() {
            val ex = assertThrows<CoreException> {
                userService.signUp(사용자_회원가입(rawPassword = "Pass한글1!"))
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @Test
        fun `비밀번호는_원문이_아닌_암호화된_값으로_저장된다`() {
            userService.signUp(사용자_회원가입(rawPassword = 기본_비밀번호))

            val saved = userJpaRepository.findByLoginId(기본_로그인_ID)
            assertThat(saved?.encodedPassword).isNotEqualTo(기본_비밀번호)
        }

        @Test
        fun `유효한_로그인ID와_비밀번호면_내_정보를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val result = userService.getMe(기본_로그인_ID, 기본_비밀번호)

            assertThat(result.loginId.value).isEqualTo(기본_로그인_ID)
            assertThat(result.name.value).isEqualTo(기본_이름)
            assertThat(result.birthday.value).isEqualTo(기본_생년월일)
            assertThat(result.email.value).isEqualTo(기본_이메일)
        }

        @Test
        fun `사용자가_없으면_UNAUTHORIZED가_발생한다`() {
            val ex = assertThrows<CoreException> {
                userService.getMe("missingUser", 기본_비밀번호)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @Test
        fun `비밀번호가_일치하지_않으면_UNAUTHORIZED가_발생한다`() {
            userService.signUp(사용자_회원가입())

            val ex = assertThrows<CoreException> {
                userService.getMe(기본_로그인_ID, "Wrongpass1!")
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @Test
        fun `비밀번호를_변경하면_새_비밀번호로_인증된다`() {
            val user = userService.signUp(사용자_회원가입())

            userService.changePassword(
                UserChangePasswordCommand(
                    userId = user.id,
                    currentRawPassword = 기본_비밀번호,
                    newRawPassword = "NewPass1!",
                ),
            )

            val oldPasswordEx = assertThrows<CoreException> {
                userService.getMe(기본_로그인_ID, 기본_비밀번호)
            }
            val result = userService.getMe(기본_로그인_ID, "NewPass1!")
            assertThat(oldPasswordEx.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            assertThat(result.loginId.value).isEqualTo(기본_로그인_ID)
        }

        @Test
        fun `현재_비밀번호가_일치하지_않으면_비밀번호_변경이_불가하다`() {
            val user = userService.signUp(사용자_회원가입())

            val ex = assertThrows<CoreException> {
                userService.changePassword(
                    UserChangePasswordCommand(
                        userId = user.id,
                        currentRawPassword = "Wrongpass1!",
                        newRawPassword = "NewPass1!",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @Test
        fun `새_비밀번호에_생년월일이_포함되면_BAD_REQUEST가_발생한다`() {
            val user = userService.signUp(사용자_회원가입())

            val ex = assertThrows<CoreException> {
                userService.changePassword(
                    UserChangePasswordCommand(
                        userId = user.id,
                        currentRawPassword = 기본_비밀번호,
                        newRawPassword = "Pw19900514!",
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @Test
        fun `아이디_선점당할시_가입실패후_409에러`() {
            val executor = Executors.newFixedThreadPool(2)

            try {
                val results = (1..2).map { index ->
                    executor.submit<Result<UserModel>> {
                        runCatching {
                            userService.signUp(
                                사용자_회원가입(
                                    loginId = RACE_LOGIN_ID,
                                    email = "race$index@example.com",
                                ),
                            )
                        }
                    }
                }.map { it.get(10, TimeUnit.SECONDS) }

                val failures = results.mapNotNull { it.exceptionOrNull() }
                assertThat(results.count { it.isSuccess }).isEqualTo(1)
                assertThat(failures).hasSize(1)
                assertThat(failures.single()).isInstanceOf(CoreException::class.java)
                assertThat((failures.single() as CoreException).errorType).isEqualTo(ErrorType.CONFLICT)
            } finally {
                executor.shutdownNow()
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw IllegalStateException("동시성 테스트 executor 종료 실패")
                }
            }
        }

        @TestConfiguration
        class RaceConditionConfig {
            @Bean
            @Primary
            fun raceAwareUserRepository(delegate: UserRepositoryImpl): UserRepository =
                RaceAwareUserRepository(delegate)
        }

        private class RaceAwareUserRepository(
            private val delegate: UserRepository,
        ) : UserRepository {
            private val barrier = CyclicBarrier(2)

            override fun existsByLoginId(loginId: String): Boolean {
                if (loginId == RACE_LOGIN_ID) {
                    awaitRaceBarrier()
                    return false
                }
                return delegate.existsByLoginId(loginId)
            }

            override fun save(user: UserModel): UserModel = delegate.save(user)

            override fun findByLoginId(loginId: String): UserModel? = delegate.findByLoginId(loginId)

            override fun findByIdForUpdate(id: Long): UserModel? = delegate.findByIdForUpdate(id)

            override fun updatePassword(id: Long, password: Password) {
                delegate.updatePassword(id, password)
            }

            private fun awaitRaceBarrier() {
                try {
                    barrier.await(5, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("동시성 테스트 동기화 실패", e)
                } catch (e: Exception) {
                    throw IllegalStateException("동시성 테스트 동기화 실패", e)
                }
            }
        }

        companion object {
            private const val RACE_LOGIN_ID = "raceUser"
        }
    }

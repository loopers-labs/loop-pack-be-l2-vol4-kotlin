package com.loopers.domain.user.unit

import com.loopers.domain.user.application.UserService
import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.exception.InvalidPasswordException
import com.loopers.domain.user.port.PasswordEncoder
import com.loopers.domain.user.port.UserRepository
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.domain.user.support.UserSteps.Companion.저장된_회원_도메인_생성
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserServiceTest {
    @Test
    fun `도메인_예외를_CoreException으로_변환할_때_원인_예외를_보존한다`() {
        val userRepository = mockk<UserRepository>()
        val passwordEncoder = mockk<PasswordEncoder>()
        val userService = UserService(userRepository, passwordEncoder)
        every { userRepository.existsByLoginId(any()) } returns false

        val ex = assertThrows<CoreException> {
            userService.signUp(사용자_회원가입(rawPassword = "Pass한글1!"))
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(ex.cause).isInstanceOf(InvalidPasswordException::class.java)
    }

    @Test
    fun `새_비밀번호가_현재_비밀번호와_같으면_BAD_REQUEST가_발생한다`() {
        val userRepository = mockk<UserRepository>()
        val passwordEncoder = mockk<PasswordEncoder>()
        val userService = UserService(userRepository, passwordEncoder)
        val encodedPassword = "encoded-current-password"
        every { userRepository.findByIdForUpdate(1L) } returns 저장된_회원_도메인_생성(
            id = 1L,
            encodedPassword = encodedPassword,
        )
        every { passwordEncoder.matches(기본_비밀번호, encodedPassword) } returns true

        val ex = assertThrows<CoreException> {
            userService.changePassword(
                UserChangePasswordCommand(
                    userId = 1L,
                    currentRawPassword = 기본_비밀번호,
                    newRawPassword = 기본_비밀번호,
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        verify(exactly = 0) { passwordEncoder.encode(any()) }
        verify(exactly = 0) { userRepository.updatePassword(any(), any()) }
    }
}

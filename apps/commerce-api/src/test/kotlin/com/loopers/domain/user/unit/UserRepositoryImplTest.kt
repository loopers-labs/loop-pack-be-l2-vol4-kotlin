package com.loopers.domain.user.unit

import com.loopers.domain.user.exception.DuplicateLoginIdException
import com.loopers.domain.user.infrastructure.persistence.USER_LOGIN_ID_UNIQUE_CONSTRAINT
import com.loopers.domain.user.infrastructure.persistence.UserJpaRepository
import com.loopers.domain.user.infrastructure.persistence.UserRepositoryImpl
import com.loopers.domain.user.support.UserSteps.Companion.회원_도메인_생성
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException

class UserRepositoryImplTest {
    @Test
    fun `login_id_유니크_제약_위반은_중복_로그인ID_예외로_변환한다`() {
        val userJpaRepository = mockk<UserJpaRepository>()
        val userRepository = UserRepositoryImpl(userJpaRepository)
        every { userJpaRepository.saveAndFlush(any()) } throws
            DataIntegrityViolationException("Duplicate entry for key '$USER_LOGIN_ID_UNIQUE_CONSTRAINT'")

        assertThrows<DuplicateLoginIdException> {
            userRepository.save(회원_도메인_생성())
        }
    }

    @Test
    fun `login_id_유니크_제약이_아닌_무결성_위반은_원본_예외를_전파한다`() {
        val userJpaRepository = mockk<UserJpaRepository>()
        val userRepository = UserRepositoryImpl(userJpaRepository)
        val exception = DataIntegrityViolationException("other constraint violation")
        every { userJpaRepository.saveAndFlush(any()) } throws exception

        assertThrows<DataIntegrityViolationException> {
            userRepository.save(회원_도메인_생성())
        }
    }
}

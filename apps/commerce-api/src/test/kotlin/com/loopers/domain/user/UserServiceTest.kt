package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceTest {
    private lateinit var userRepositoryPort: UserRepositoryPort
    private lateinit var userService: UserService

    private val defaultBirth: LocalDate = LocalDate.of(1995, 3, 15)

    @BeforeEach
    fun setUp() {
        userRepositoryPort = mockk()
        userService = UserService(userRepositoryPort)
    }

    @DisplayName("create를 호출할 때, ")
    @Nested
    inner class Create {
        @DisplayName("User가 저장되어 반환된다.")
        @Test
        fun savesUserAndReturns() {
            val userSlot = slot<User>()
            every { userRepositoryPort.save(capture(userSlot)) } answers { userSlot.captured.copy(id = 100L) }

            val result = userService.create(name = "홍길동", birth = defaultBirth, email = "test@example.com")

            assertThat(result.id).isEqualTo(100L)
            assertThat(result.name).isEqualTo("홍길동")
            assertThat(result.birth).isEqualTo(defaultBirth)
            assertThat(result.email).isEqualTo("test@example.com")
            verify(exactly = 1) { userRepositoryPort.save(any()) }
        }
    }

    @DisplayName("getById를 호출할 때, ")
    @Nested
    inner class GetById {
        @DisplayName("사용자가 존재하면, 해당 사용자를 반환한다.")
        @Test
        fun returnsUser_whenExists() {
            val user = User(id = 100L, name = "홍길동", birth = defaultBirth, email = "test@example.com")
            every { userRepositoryPort.findById(100L) } returns user

            val result = userService.getById(100L)

            assertThat(result).isEqualTo(user)
        }

        @DisplayName("사용자가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserMissing() {
            every { userRepositoryPort.findById(100L) } returns null

            val result = assertThrows<CoreException> { userService.getById(100L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}

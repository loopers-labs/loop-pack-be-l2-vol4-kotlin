package com.loopers.infrastructure.user

import com.loopers.application.user.UserRepository
import com.loopers.domain.user.Password
import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserRepositoryImplIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save를 호출할 때,")
    @Nested
    inner class Save {
        @DisplayName("id가 없는 User를 저장하면, DB에 저장되고 id가 부여된다.")
        @Test
        fun savesNewUser_whenIdIsZero() {
            // arrange
            val user = User(
                loginId = "testuser",
                password = Password("hashedpassword"),
                name = "테스트",
                birth = LocalDate.of(1990, 1, 1),
                email = "test@test.com",
            )

            // act
            val saved = userRepository.save(user)

            // assert
            assertAll(
                { assertThat(saved.id).isGreaterThan(0) },
                { assertThat(saved.loginId).isEqualTo("testuser") },
                { assertThat(saved.name).isEqualTo("테스트") },
                { assertThat(saved.email).isEqualTo("test@test.com") },
            )

            val found = userJpaRepository.findById(saved.id)
            assertThat(found).isPresent
        }

        @DisplayName("id가 존재하는 User를 저장하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsException_whenIdExists() {
            // arrange
            val user = User(
                id = 1L,
                loginId = "testuser",
                password = Password("hashedpassword"),
                name = "테스트",
                birth = LocalDate.of(1990, 1, 1),
                email = "test@test.com",
            )

            // act
            val exception = assertThrows<CoreException> {
                userRepository.save(user)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}

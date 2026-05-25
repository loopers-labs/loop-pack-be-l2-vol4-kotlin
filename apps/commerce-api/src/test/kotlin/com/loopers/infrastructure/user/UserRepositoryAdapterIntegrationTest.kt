package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepositoryPort
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
class UserRepositoryAdapterIntegrationTest @Autowired constructor(
    private val userRepositoryPort: UserRepositoryPort,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("id가 없는 User를 저장하면, id가 부여되고 DB에 저장된다.")
        @Test
        fun savesNewUser_whenIdIsZero() {
            // arrange
            val user = User(name = "테스트", birth = LocalDate.of(1990, 1, 1), email = "test@test.com")

            // act
            val saved = userRepositoryPort.save(user)

            // assert
            assertAll(
                { assertThat(saved.id).isGreaterThan(0L) },
                { assertThat(saved.name).isEqualTo("테스트") },
                { assertThat(saved.email).isEqualTo("test@test.com") },
            )
            assertThat(userJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("id가 존재하는 User를 저장하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsException_whenIdExists() {
            // arrange
            val user = User(id = 1L, name = "테스트", birth = LocalDate.of(1990, 1, 1), email = "test@test.com")

            // act
            val result = assertThrows<CoreException> { userRepositoryPort.save(user) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("findByIdOrNull을 호출할 때, ")
    @Nested
    inner class FindByIdOrNull {
        @DisplayName("해당 id의 User가 있으면, 도메인 객체를 반환한다.")
        @Test
        fun returnsUser_whenExists() {
            // arrange
            val saved = userRepositoryPort.save(
                User(name = "테스트", birth = LocalDate.of(1990, 1, 1), email = "test@test.com"),
            )

            // act
            val found = userRepositoryPort.findByIdOrNull(saved.id)

            // assert
            assertThat(found).isNotNull
            assertThat(found?.id).isEqualTo(saved.id)
            assertThat(found?.name).isEqualTo("테스트")
        }

        @DisplayName("해당 id의 User가 없으면, null을 반환한다.")
        @Test
        fun returnsNull_whenNotExists() {
            // act
            val found = userRepositoryPort.findByIdOrNull(9999L)

            // assert
            assertThat(found).isNull()
        }
    }
}

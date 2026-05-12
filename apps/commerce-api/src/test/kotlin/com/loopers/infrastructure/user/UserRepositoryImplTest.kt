package com.loopers.infrastructure.user

import com.loopers.application.user.UserRepository
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
class UserRepositoryImplTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("upsert를 호출할 때,")
    @Nested
    inner class Upsert {
        @DisplayName("id가 없는 User를 저장하면, DB에 저장되고 id가 부여된다.")
        @Test
        fun savesNewUser_whenIdIsZero() {
            // arrange
            val user = User(
                loginId = "testuser",
                password = "hashedpassword",
                name = "테스트",
                birth = LocalDate.of(1990, 1, 1),
                email = "test@test.com",
            )

            // act
            val saved = userRepository.upsert(user)

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

        @DisplayName("id가 있고 DB에 존재하면, 해당 데이터가 업데이트된다.")
        @Test
        fun updatesUser_whenIdExistsInDb() {
            // arrange
            val entity = userJpaRepository.save(
                UserEntity(
                    loginId = "testuser",
                    password = "hashedpassword",
                    name = "원래이름",
                    birth = LocalDate.of(1990, 1, 1),
                    email = "old@test.com",
                ),
            )

            val updateUser = User(
                id = entity.id,
                loginId = "testuser",
                password = "newpassword",
                name = "변경이름",
                birth = LocalDate.of(1990, 1, 1),
                email = "new@test.com",
            )

            // act
            val updated = userRepository.upsert(updateUser)

            // assert
            assertAll(
                { assertThat(updated.id).isEqualTo(entity.id) },
                { assertThat(updated.name).isEqualTo("변경이름") },
                { assertThat(updated.email).isEqualTo("new@test.com") },
                { assertThat(updated.password).isEqualTo("newpassword") },
            )
        }

        @DisplayName("id가 있지만 DB에 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsException_whenIdNotFoundInDb() {
            // arrange
            val user = User(
                id = 999L,
                loginId = "testuser",
                password = "hashedpassword",
                name = "테스트",
                birth = LocalDate.of(1990, 1, 1),
                email = "test@test.com",
            )

            // act
            val exception = assertThrows<CoreException> {
                userRepository.upsert(user)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}

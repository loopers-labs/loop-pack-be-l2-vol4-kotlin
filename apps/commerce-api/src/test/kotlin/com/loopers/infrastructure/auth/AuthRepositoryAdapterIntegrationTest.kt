package com.loopers.infrastructure.auth

import com.loopers.domain.auth.Auth
import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

@SpringBootTest
class AuthRepositoryAdapterIntegrationTest @Autowired constructor(
    private val authRepositoryPort: AuthRepositoryPort,
    private val authJpaRepository: AuthJpaRepository,
    private val transactionTemplate: TransactionTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val defaultBirth: LocalDate = LocalDate.of(2000, 1, 1)

    private fun newAuth(
        userId: Long = 1L,
        loginId: String = "testuser",
        rawPassword: String = "Password1!",
        birth: LocalDate = defaultBirth,
    ): Auth = Auth.create(userId = userId, loginId = loginId, rawPassword = rawPassword, birth = birth)

    @DisplayName("save를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("id가 없는 Auth를 저장하면, id가 부여되고 DB에 저장된다.")
        @Test
        fun savesNewAuth_whenIdIsZero() {
            // arrange
            val auth = newAuth(userId = 7L, loginId = "testuser")

            // act
            val saved = authRepositoryPort.save(auth)

            // assert
            assertAll(
                { assertThat(saved.id).isGreaterThan(0L) },
                { assertThat(saved.userId).isEqualTo(7L) },
                { assertThat(saved.loginId).isEqualTo("testuser") },
            )
            assertThat(authJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("id가 존재하는 Auth를 저장하면, 비밀번호가 영속 entity에 반영된다.")
        @Test
        fun updatesPassword_whenIdExists() {
            // arrange
            val initial = authRepositoryPort.save(newAuth(userId = 1L, loginId = "testuser"))
            val updatedDomain = initial.changePassword("Password1!", "NewPass1!!", defaultBirth)

            // act
            transactionTemplate.execute {
                authRepositoryPort.save(updatedDomain)
            }

            // assert
            val reloaded = authJpaRepository.findById(initial.id).get()
            assertThat(reloaded.password).isEqualTo(updatedDomain.password.value)
        }
    }

    @DisplayName("findByLoginId을 호출할 때, ")
    @Nested
    inner class FindByLoginIdOrNull {
        @DisplayName("해당 loginId의 Auth가 있으면, 도메인 객체를 반환한다.")
        @Test
        fun returnsAuth_whenExists() {
            // arrange
            val saved = authRepositoryPort.save(newAuth(userId = 1L, loginId = "testuser"))

            // act
            val found = authRepositoryPort.findByLoginId("testuser")

            // assert
            assertThat(found).isNotNull
            assertThat(found?.id).isEqualTo(saved.id)
            assertThat(found?.userId).isEqualTo(saved.userId)
            assertThat(found?.loginId).isEqualTo("testuser")
        }

        @DisplayName("해당 loginId의 Auth가 없으면, null을 반환한다.")
        @Test
        fun returnsNull_whenNotExists() {
            // act
            val found = authRepositoryPort.findByLoginId("unknown")

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("findByUserId을 호출할 때, ")
    @Nested
    inner class FindByUserIdOrNull {
        @DisplayName("해당 userId의 Auth가 있으면, 도메인 객체를 반환한다.")
        @Test
        fun returnsAuth_whenExists() {
            // arrange
            val saved = authRepositoryPort.save(newAuth(userId = 42L, loginId = "testuser"))

            // act
            val found = authRepositoryPort.findByUserId(42L)

            // assert
            assertThat(found).isNotNull
            assertThat(found?.id).isEqualTo(saved.id)
            assertThat(found?.loginId).isEqualTo("testuser")
        }

        @DisplayName("해당 userId의 Auth가 없으면, null을 반환한다.")
        @Test
        fun returnsNull_whenNotExists() {
            // act
            val found = authRepositoryPort.findByUserId(999L)

            // assert
            assertThat(found).isNull()
        }
    }

    @DisplayName("existsByLoginId를 호출할 때, ")
    @Nested
    inner class ExistsByLoginId {
        @DisplayName("해당 loginId의 Auth가 있으면, true를 반환한다.")
        @Test
        fun returnsTrue_whenExists() {
            // arrange
            authRepositoryPort.save(newAuth(loginId = "testuser"))

            // act
            val result = authRepositoryPort.existsByLoginId("testuser")

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("해당 loginId의 Auth가 없으면, false를 반환한다.")
        @Test
        fun returnsFalse_whenNotExists() {
            // act
            val result = authRepositoryPort.existsByLoginId("unknown")

            // assert
            assertThat(result).isFalse()
        }
    }
}

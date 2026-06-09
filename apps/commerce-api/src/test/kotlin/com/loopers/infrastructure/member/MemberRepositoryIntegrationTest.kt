package com.loopers.infrastructure.member

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.user.UserRepository
import com.loopers.fixture.user.UserFixture
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MemberRepositoryImpl::class, MySqlTestContainersConfig::class, DataSourceConfig::class)
class MemberRepositoryIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val memberJpaRepository: MemberJpaRepository,
) {
    @DisplayName("회원 저장")
    @Nested
    inner class Save {
        @DisplayName("회원을 저장하면 로그인 ID 존재 여부를 확인할 수 있다")
        @Test
        fun returnsTrue_whenUserExistsByLoginId() {
            val user = UserFixture.createUser(loginId = "loopers123")

            val savedUser = userRepository.save(user)

            assertAll(
                { assertThat(savedUser.id).isNotNull() },
                { assertThat(userRepository.existsByLoginId("loopers123")).isTrue() },
            )
        }

        @DisplayName("동일한 로그인 ID 로 회원을 중복 저장할 수 없다")
        @Test
        fun throwsDataIntegrityViolation_whenLoginIdIsDuplicated() {
            memberJpaRepository.saveAndFlush(
                MemberMapper.toEntity(UserFixture.createUser(loginId = "loopers123")),
            )

            val result = assertThrows<DataIntegrityViolationException> {
                memberJpaRepository.saveAndFlush(
                    MemberMapper.toEntity(
                        UserFixture.createUser(
                            loginId = "loopers123",
                            email = "other@gmail.com",
                        ),
                    ),
                )
            }

            assertThat(result).isNotNull()
        }
    }
}

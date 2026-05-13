package com.loopers.infrastructure.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(MemberRepositoryImpl::class)
class MemberRepositoryIntegrationTest @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val memberJpaRepository: MemberJpaRepository,
) {
    @DisplayName("회원 저장")
    @Nested
    inner class Save {
        @DisplayName("회원을 저장하면 로그인 ID 존재 여부를 확인할 수 있다")
        @Test
        fun returnsTrue_whenMemberExistsByLoginId() {
            val member = createMember(loginId = "loopers123")

            val savedMember = memberRepository.save(member)

            assertAll(
                { assertThat(savedMember.id).isNotNull() },
                { assertThat(memberRepository.existsByLoginId("loopers123")).isTrue() },
            )
        }

        @DisplayName("동일한 로그인 ID 로 회원을 중복 저장할 수 없다")
        @Test
        fun throwsDataIntegrityViolation_whenLoginIdIsDuplicated() {
            memberJpaRepository.saveAndFlush(createMember(loginId = "loopers123"))

            val result = assertThrows<DataIntegrityViolationException> {
                memberJpaRepository.saveAndFlush(createMember(loginId = "loopers123", email = "other@gmail.com"))
            }

            assertThat(result).isNotNull()
        }

        private fun createMember(
            loginId: String = "loopers123",
            password: String = "encodedPassword",
            name: String = "gunyoung",
            birthDate: LocalDate = LocalDate.of(1995, 5, 20),
            email: String = "loopers@gmail.com",
        ): Member =
            Member(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
    }
}

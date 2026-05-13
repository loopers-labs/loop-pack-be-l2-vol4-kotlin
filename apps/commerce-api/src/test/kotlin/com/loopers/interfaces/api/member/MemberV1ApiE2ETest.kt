package com.loopers.interfaces.api.member

import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.junit.jupiter.api.AfterEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/members")
    @Nested
    inner class SignUp {
        @DisplayName("회원가입 요청이 유효하면 회원가입에 성공한다")
        @Test
        fun returnsSuccess_whenSignUpRequestIsValid() {
            val request = createSignUpRequest()

            val response = testRestTemplate.exchange(
                SIGN_UP_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.SignUpResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(request.loginId) },
                { assertThat(memberJpaRepository.existsByLoginId(request.loginId)).isTrue() },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            val request = createSignUpRequest(loginId = "loopers123")
            testRestTemplate.postForEntity(SIGN_UP_ENDPOINT, request, String::class.java)

            val response = testRestTemplate.exchange(
                SIGN_UP_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createSignUpRequest(loginId = "loopers123", email = "other@gmail.com")),
                object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.SignUpResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    private fun createSignUpRequest(
        loginId: String = "loopers123",
        password: String = "Loopers123!",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): MemberV1Dto.SignUpRequest =
        MemberV1Dto.SignUpRequest(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

    companion object {
        private const val SIGN_UP_ENDPOINT = "/api/v1/members"
    }
}

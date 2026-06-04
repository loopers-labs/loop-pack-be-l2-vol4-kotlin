package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.user.application.UserService
import com.loopers.domain.user.presentation.response.MyUserResponse
import com.loopers.domain.user.presentation.response.SignUpResponse
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.기본_이름
import com.loopers.domain.user.support.UserSteps.Companion.기본_이메일
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.domain.user.support.UserSteps.Companion.회원가입_요청_생성
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class UserApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
    ) : ApiTest() {
        companion object {
            private const val ENDPOINT = "/api/users"
            private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
            private const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        }

        private val signUpResponseType =
            object : ParameterizedTypeReference<ApiResponse<SignUpResponse>>() {}
        private val myUserResponseType =
            object : ParameterizedTypeReference<ApiResponse<MyUserResponse>>() {}
        private val successResponseType =
            object : ParameterizedTypeReference<ApiResponse<Any>>() {}

        @Test
        fun `유효한_요청이면_201_CREATED와_가입된_회원_정보를_반환한다`() {
            val request = 회원가입_요청_생성()

            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), signUpResponseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(response.body?.data?.loginId).isEqualTo(기본_로그인_ID)
            assertThat(response.body?.data?.name).isEqualTo(기본_이름)
            assertThat(response.body?.data?.email).isEqualTo(기본_이메일)
        }

        @Test
        fun `비밀번호에_생년월일이_포함되면_400_BAD_REQUEST를_반환한다`() {
            val request = 회원가입_요청_생성(password = "Pw19900514!")

            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), signUpResponseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `이메일_형식이_잘못되면_400_BAD_REQUEST를_반환한다`() {
            val request = 회원가입_요청_생성(email = "not-an-email")

            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), signUpResponseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `중복_로그인ID로_가입하면_409_CONFLICT를_반환한다`() {
            userService.signUp(사용자_회원가입(loginId = "duplicate", email = "existing@example.com"))
            val request = 회원가입_요청_생성(loginId = "duplicate", email = "new@example.com")

            val response = testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(request), signUpResponseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `올바른_인증_헤더면_200_OK와_마스킹된_내_정보를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = getMe(authHeaders())

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.loginId).isEqualTo(기본_로그인_ID)
            assertThat(response.body?.data?.name).isEqualTo("홍길*")
            assertThat(response.body?.data?.email).isEqualTo(기본_이메일)
        }

        @Test
        fun `로그인ID_헤더가_없으면_401_UNAUTHORIZED를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = getMe(authHeaders(loginId = null))

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `비밀번호_헤더가_없으면_401_UNAUTHORIZED를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = getMe(authHeaders(password = null))

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `로그인ID_헤더가_blank면_401_UNAUTHORIZED를_반환한다`() {
            userService.signUp(사용자_회원가입())

            listOf("", "   ").forEach { blankLoginId ->
                val response = getMe(authHeaders(loginId = blankLoginId))

                assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            }
        }

        @Test
        fun `비밀번호_헤더가_blank면_401_UNAUTHORIZED를_반환한다`() {
            userService.signUp(사용자_회원가입())

            listOf("", "   ").forEach { blankPassword ->
                val response = getMe(authHeaders(password = blankPassword))

                assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            }
        }

        @Test
        fun `존재하지_않는_사용자면_401_UNAUTHORIZED를_반환한다`() {
            val response = getMe(authHeaders(loginId = "missingUser"))

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `비밀번호가_틀리면_401_UNAUTHORIZED를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = getMe(authHeaders(password = "Wrongpass1!"))

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `올바른_인증_헤더와_새_비밀번호면_비밀번호가_변경된다`() {
            userService.signUp(사용자_회원가입())

            val response = changePassword(authHeaders(), "NewPass1!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(getMe(authHeaders()).statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(getMe(authHeaders(password = "NewPass1!")).statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        fun `새_비밀번호에_생년월일이_포함되면_400_BAD_REQUEST를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = changePassword(authHeaders(), "Pw19900514!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `새_비밀번호가_현재_비밀번호와_같으면_400_BAD_REQUEST를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = changePassword(authHeaders(), 기본_비밀번호)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `이름이_한_글자면_별표만_반환한다`() {
            userService.signUp(
                사용자_회원가입(
                    loginId = "singleName",
                    name = "김",
                    email = "single@example.com",
                ),
            )

            val response = getMe(authHeaders(loginId = "singleName"))

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.name).isEqualTo("*")
        }

        private fun getMe(headers: HttpHeaders) =
            testRestTemplate.exchange("$ENDPOINT/me", HttpMethod.GET, HttpEntity<Unit>(headers), myUserResponseType)

        private fun changePassword(
            headers: HttpHeaders,
            newPassword: String,
        ) = testRestTemplate.exchange(
            "$ENDPOINT/me/password",
            HttpMethod.PATCH,
            HttpEntity(mapOf("newPassword" to newPassword), headers),
            successResponseType,
        )

        private fun authHeaders(
            loginId: String? = 기본_로그인_ID,
            password: String? = 기본_비밀번호,
        ): HttpHeaders {
            val headers = HttpHeaders()
            loginId?.let { headers.set(LOGIN_ID_HEADER, it) }
            password?.let { headers.set(LOGIN_PW_HEADER, it) }
            return headers
        }
    }

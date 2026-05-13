package com.loopers.interfaces.api

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserService
import com.loopers.domain.user.User
import com.loopers.interfaces.api.user.UserController
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate

@WebMvcTest(UserController::class)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var userFacade: UserFacade

    @DisplayName("회원가입 API를 호출하면, UserService의 signup 메서드를 1번 이상 호출한다.")
    @Test
    fun callsServiceSignup_whenSignupApiIsCalled() {
        // given
        every { userService.signup(any()) } returns Unit

        // when
        mockMvc.post("/api/v1/user/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "id": "testuser01",
                    "pw": "Password1!",
                    "name": "홍길동",
                    "birth": "1995-03-15",
                    "email": "test@example.com"
                }
            """.trimIndent()
        }

        // then
        verify(atLeast = 1) { userService.signup(any()) }
    }

    @DisplayName("유저 정보 조회 API를 호출할 때,")
    @Nested
    inner class GetUserInfo {

        @DisplayName("로그인에 성공하면, 유저 정보를 반환한다.")
        @Test
        fun returnsUserInfo_whenLoginSucceeds() {
            // arrange
            val loginId = "testuser01"
            val password = "Password1!"
            val user = User(
                id = 1L,
                loginId = loginId,
                password = "encryptedPassword",
                name = "홍길동",
                birth = LocalDate.of(1995, 3, 15),
                email = "test@example.com",
            )
            every { userService.login(loginId, password) } returns user

            // act & assert
            mockMvc.get("/api/v1/user/info") {
                header("X-Loopers-LoginId", loginId)
                header("X-Loopers-LoginPw", password)
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.loginId") { value("testuser01") }
                jsonPath("$.data.name") { value("홍길*") }
                jsonPath("$.data.birth") { value("1995-03-15") }
                jsonPath("$.data.email") { value("test@example.com") }
                jsonPath("$.meta.result") { value("SUCCESS") }
            }

            verify(exactly = 1) { userService.login(loginId, password) }
        }

        @DisplayName("로그인 인증에 실패하면, 401 인증 예외가 발생한다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            // arrange
            val loginId = "wronguser"
            val password = "WrongPass1!"
            every { userService.login(loginId, password) } throws CoreException(
                ErrorType.UNAUTHORIZED,
                "아이디 또는 비밀번호가 올바르지 않습니다.",
            )

            // act & assert
            mockMvc.get("/api/v1/user/info") {
                header("X-Loopers-LoginId", loginId)
                header("X-Loopers-LoginPw", password)
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.meta.result") { value("FAIL") }
                jsonPath("$.meta.errorCode") { value("Unauthorized") }
            }

            verify(exactly = 1) { userService.login(loginId, password) }
        }
    }

    @DisplayName("비밀번호 변경 API를 호출할 때,")
    @Nested
    inner class ChangePassword {

        @DisplayName("비밀번호 변경에 성공하면, 성공 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenChangePasswordSucceeds() {
            // arrange
            val loginId = "testuser01"
            val loginPw = "Password1!"
            every { userFacade.changePw(any()) } just runs

            // act & assert
            mockMvc.put("/api/v1/user/password") {
                header("X-Loopers-LoginId", loginId)
                header("X-Loopers-LoginPw", loginPw)
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "prevPw": "Password1!",
                        "nextPw": "NewPassword1!"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.meta.result") { value("SUCCESS") }
            }

            verify(exactly = 1) { userFacade.changePw(any()) }
        }

        @DisplayName("인증에 실패하면, 401 인증 예외가 발생한다.")
        @Test
        fun returnsUnauthorized_whenAuthenticationFails() {
            // arrange
            val loginId = "testuser01"
            val loginPw = "WrongPass1!"
            every { userFacade.changePw(any()) } throws CoreException(
                ErrorType.UNAUTHORIZED,
                "아이디 또는 비밀번호가 올바르지 않습니다.",
            )

            // act & assert
            mockMvc.put("/api/v1/user/password") {
                header("X-Loopers-LoginId", loginId)
                header("X-Loopers-LoginPw", loginPw)
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "prevPw": "WrongPass1!",
                        "nextPw": "NewPassword1!"
                    }
                """.trimIndent()
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.meta.result") { value("FAIL") }
                jsonPath("$.meta.errorCode") { value("Unauthorized") }
            }

            verify(exactly = 1) { userFacade.changePw(any()) }
        }

        @DisplayName("잘못된 요청이면, 400 에러가 발생한다.")
        @Test
        fun returnsBadRequest_whenRequestIsInvalid() {
            // arrange
            val loginId = "testuser01"
            val loginPw = "Password1!"
            every { userFacade.changePw(any()) } throws CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자여야 합니다.",
            )

            // act & assert
            mockMvc.put("/api/v1/user/password") {
                header("X-Loopers-LoginId", loginId)
                header("X-Loopers-LoginPw", loginPw)
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "prevPw": "Password1!",
                        "nextPw": "short"
                    }
                """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.meta.result") { value("FAIL") }
                jsonPath("$.meta.errorCode") { value("Bad Request") }
            }

            verify(exactly = 1) { userFacade.changePw(any()) }
        }
    }
}

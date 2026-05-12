package com.loopers.interfaces.api

import com.loopers.application.UserFacade
import com.loopers.interfaces.api.user.UserController
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(UserController::class)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var userFacade: UserFacade

    @DisplayName("회원가입 API를 호출하면, UserFacade의 signup 메서드를 1번 이상 호출한다.")
    @Test
    fun callsFacadeSignup_whenSignupApiIsCalled() {
        // given
        every { userFacade.signup(any()) } returns Unit

        // when
        mockMvc.post("/api/v1/user/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "id": "testuser01",
                    "pw": "password1234",
                    "name": "홍길동",
                    "birth": "1995-03-15",
                    "email": "test@example.com"
                }
            """.trimIndent()
        }

        // then
        verify(atLeast = 1) { userFacade.signup(any()) }
    }
}

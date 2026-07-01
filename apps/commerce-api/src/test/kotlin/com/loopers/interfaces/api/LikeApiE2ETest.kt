package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class LikeApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
    ) : ApiTest() {
        private val successResponseType =
            object : ParameterizedTypeReference<ApiResponse<Any>>() {}

        @Test
        fun `존재하지_않는_상품에_좋아요하면_404_NOT_FOUND를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = testRestTemplate.exchange(
                "/api/v1/products/999999/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                successResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `존재하지_않는_상품의_좋아요를_취소하면_404_NOT_FOUND를_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = testRestTemplate.exchange(
                "/api/v1/products/999999/likes",
                HttpMethod.DELETE,
                HttpEntity<Any>(authHeaders()),
                successResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        private fun authHeaders(): HttpHeaders =
            HttpHeaders().apply {
                set("X-Loopers-LoginId", 기본_로그인_ID)
                set("X-Loopers-LoginPw", 기본_비밀번호)
            }
    }

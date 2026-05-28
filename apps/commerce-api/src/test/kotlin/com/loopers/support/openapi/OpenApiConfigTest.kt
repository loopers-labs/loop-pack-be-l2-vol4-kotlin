package com.loopers.support.openapi

import com.loopers.support.auth.Admin
import io.swagger.v3.oas.models.Operation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.method.HandlerMethod

class OpenApiConfigTest {
    @DisplayName("@Admin method operations require login headers in OpenAPI.")
    @Test
    fun addsLoginHeaderSecurity_whenMethodRequiresAdmin() {
        val customizer = OpenApiConfig().loginRequiredSecurityCustomizer()
        val operation = Operation()

        val customized = customizer.customize(operation, handlerMethod("adminEndpoint"))

        assertThat(customized.security).hasSize(1)
        assertThat(customized.security.first().keys).containsExactlyInAnyOrder("LoginId", "LoginPw")
    }

    private fun handlerMethod(methodName: String): HandlerMethod {
        val method = TestController::class.java.getDeclaredMethod(methodName)
        return HandlerMethod(TestController(), method)
    }

    private class TestController {
        @Admin
        fun adminEndpoint() {
        }
    }
}

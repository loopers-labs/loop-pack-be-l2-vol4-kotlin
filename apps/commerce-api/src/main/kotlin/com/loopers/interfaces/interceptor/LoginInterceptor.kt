package com.loopers.interfaces.interceptor

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class LoginInterceptor(
    private val userFacade: UserFacade,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val loginId = request.getHeader("X-Loopers-LoginId")
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "X-Loopers-LoginId 헤더가 없습니다.")
        val rawPassword = request.getHeader("X-Loopers-LoginPw")
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "X-Loopers-LoginPw 헤더가 없습니다.")

        val userInfo = userFacade.authenticate(loginId, rawPassword)
        request.setAttribute(LOGIN_USER_ATTRIBUTE, userInfo)
        return true
    }

    companion object {
        const val LOGIN_USER_ATTRIBUTE = "loginUser"
    }
}

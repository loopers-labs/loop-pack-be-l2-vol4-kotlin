package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.EntryTokenRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class EntryTokenInterceptor(
    private val entryTokenRepository: EntryTokenRepository,
) : HandlerInterceptor {

    companion object {
        const val TOKEN_HEADER = "X-Loopers-Queue-Token"
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method != "POST") {
            return true
        }

        val loginId = request.getHeader(LOGIN_ID_HEADER)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "$LOGIN_ID_HEADER 헤더가 필요합니다.")
        val token = request.getHeader(TOKEN_HEADER)
            ?: throw CoreException(ErrorType.FORBIDDEN, "대기열 입장 토큰이 필요합니다. 대기열에 먼저 진입해 주세요.")

        val issued = entryTokenRepository.find(loginId)
        if (issued == null || issued != token) {
            throw CoreException(ErrorType.FORBIDDEN, "유효하지 않거나 만료된 대기열 토큰입니다.")
        }

        entryTokenRepository.delete(loginId)
        return true
    }
}

package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.EntryTokenRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.lang.Exception

@Component
class EntryTokenInterceptor(
    private val entryTokenRepository: EntryTokenRepository,
) : HandlerInterceptor {
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

        // 검증만 하고 소모는 미룬다. 실제 삭제는 주문 성공 후 afterCompletion 에서.
        request.setAttribute(VALIDATED_LOGIN_ID, loginId)
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        val loginId = request.getAttribute(VALIDATED_LOGIN_ID) as? String ?: return

        if (ex == null && response.status in 200..299) {
            entryTokenRepository.delete(loginId)
        }
    }

    companion object {
        const val TOKEN_HEADER = "X-Loopers-Queue-Token"
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        private const val VALIDATED_LOGIN_ID = "queue.validatedLoginId"
    }
}

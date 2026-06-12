package com.loopers.interfaces.api

import com.loopers.support.error.ErrorType
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.ObjectError
import org.springframework.web.bind.MethodArgumentNotValidException

class ApiControllerAdviceTest {
    private val advice = ApiControllerAdvice()

    @Test
    fun `validation_global_error를_응답_메시지에_반영한다`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(ObjectError("request", "클래스 레벨 오류"))
        val exception = MethodArgumentNotValidException(mockk<MethodParameter>(), bindingResult)

        val response = advice.handleBadRequest(exception)

        assertThat(response.body?.meta?.message).isEqualTo("클래스 레벨 오류")
    }

    @Test
    fun `validation_메시지가_비어있으면_BAD_REQUEST_기본_메시지를_사용한다`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        val exception = MethodArgumentNotValidException(mockk<MethodParameter>(), bindingResult)

        val response = advice.handleBadRequest(exception)

        assertThat(response.body?.meta?.message).isEqualTo(ErrorType.BAD_REQUEST.message)
    }
}

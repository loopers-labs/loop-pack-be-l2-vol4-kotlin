package com.loopers.domain.example

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExampleModelTest {
    @Test
    fun `이름과_설명이_있으면_예시_모델이_생성된다`() {
        val name = "제목"
        val description = "설명"

        val exampleModel = ExampleModel(name = name, description = description)

        assertThat(exampleModel.id).isNotNull()
        assertThat(exampleModel.name).isEqualTo(name)
        assertThat(exampleModel.description).isEqualTo(description)
    }

    @Test
    fun `이름이_공백이면_BAD_REQUEST가_발생한다`() {
        val ex = assertThrows<CoreException> {
            ExampleModel(name = "   ", description = "설명")
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `설명이_비어있으면_BAD_REQUEST가_발생한다`() {
        val ex = assertThrows<CoreException> {
            ExampleModel(name = "제목", description = "")
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}

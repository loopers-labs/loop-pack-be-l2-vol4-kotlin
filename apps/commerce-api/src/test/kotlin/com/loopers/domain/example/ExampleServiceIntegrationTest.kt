package com.loopers.domain.example

import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ExampleServiceIntegrationTest
    @Autowired
    constructor(
        private val exampleService: ExampleService,
        private val exampleJpaRepository: ExampleJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `존재하는_예시_ID면_예시_정보를_반환한다`() {
            val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명"))

            val result = exampleService.getExample(exampleModel.id)

            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(exampleModel.id)
            assertThat(result.name).isEqualTo(exampleModel.name)
            assertThat(result.description).isEqualTo(exampleModel.description)
        }

        @Test
        fun `존재하지_않는_예시_ID면_NOT_FOUND가_발생한다`() {
            val ex = assertThrows<CoreException> {
                exampleService.getExample(999L)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

package com.loopers.domain.brand.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.infrastructure.persistence.BrandJpaRepository
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
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
class BrandServiceIntegrationTest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val brandJpaRepository: BrandJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `유효한_커맨드면_브랜드가_저장된다`() {
            val brand = brandService.register(브랜드_등록_커맨드())

            val saved = brandJpaRepository.findById(brand.id).orElseThrow()
            assertThat(saved.brandName).isEqualTo("기본 브랜드")
        }

        @Test
        fun `삭제된_브랜드는_조회가_불가하다`() {
            val brand = brandService.register(브랜드_등록_커맨드())

            brandService.softDelete(brand.id)

            val ex = assertThrows<CoreException> {
                brandService.findById(brand.id)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

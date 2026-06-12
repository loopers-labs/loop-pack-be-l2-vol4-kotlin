package com.loopers.domain.brand.integration

import com.loopers.domain.brand.exception.BrandNotFoundException
import com.loopers.domain.brand.infrastructure.persistence.BrandJpaRepository
import com.loopers.domain.brand.port.BrandRepository
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_도메인_생성
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandRepositoryIntegrationTest
    @Autowired
    constructor(
        private val brandRepository: BrandRepository,
        private val brandJpaRepository: BrandJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `새_브랜드는_ID가_0이면_생성된다`() {
            val saved = brandRepository.save(브랜드_도메인_생성(id = 0L))

            assertThat(saved.id).isPositive()
            assertThat(brandJpaRepository.findById(saved.id)).isPresent
        }

        @Test
        fun `기존_브랜드는_수정된다`() {
            val saved = brandRepository.save(브랜드_도메인_생성(id = 0L))

            val updated = brandRepository.save(saved.rename(브랜드_도메인_생성(name = "변경 브랜드").name))

            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.name.value).isEqualTo("변경 브랜드")
        }

        @Test
        fun `존재하지_않는_ID의_브랜드_수정은_도메인_예외가_발생한다`() {
            assertThrows<BrandNotFoundException> {
                brandRepository.save(브랜드_도메인_생성(id = 999_999L))
            }
        }
    }

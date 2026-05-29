package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort

@DataJpaTest
class BrandRepositoryIntegrationTest @Autowired constructor(
    private val brandJpaRepository: BrandJpaRepository,
) {
    private val latestFirst = Sort.by(Sort.Direction.DESC, "id")

    @DisplayName("브랜드를 저장하면, 삭제되지 않은 단건 조회로 찾을 수 있다.")
    @Test
    fun findsActiveBrand_whenSaved() {
        val saved = brandJpaRepository.save(Brand(BrandName("나이키")))

        val found = brandJpaRepository.findByIdAndDeletedAtIsNull(saved.id)

        assertThat(found?.name).isEqualTo(BrandName("나이키"))
    }

    @DisplayName("soft delete된 브랜드는, 삭제되지 않은 단건 조회로 찾을 수 없다.")
    @Test
    fun doesNotFindDeletedBrand() {
        val brand = Brand(BrandName("나이키")).also { it.delete() }
        val saved = brandJpaRepository.save(brand)

        val found = brandJpaRepository.findByIdAndDeletedAtIsNull(saved.id)

        assertThat(found).isNull()
    }

    @DisplayName("이름 존재 여부를 조회한다.")
    @Test
    fun returnsExistsByName() {
        brandJpaRepository.save(Brand(BrandName("나이키")))

        assertAll(
            { assertThat(brandJpaRepository.existsByNameValue("나이키")).isTrue() },
            { assertThat(brandJpaRepository.existsByNameValue("아디다스")).isFalse() },
        )
    }

    @DisplayName("같은 이름의 브랜드를 중복 저장하면, DB unique 제약으로 실패한다.")
    @Test
    fun throwsDataIntegrityViolation_whenNameIsDuplicated() {
        brandJpaRepository.saveAndFlush(Brand(BrandName("나이키")))

        assertThrows<DataIntegrityViolationException> {
            brandJpaRepository.saveAndFlush(Brand(BrandName("나이키")))
        }
    }

    @DisplayName("keyset 페이지네이션은 id DESC 정렬로 size만큼 반환하고, 다음 페이지 존재 여부를 알려준다.")
    @Test
    fun returnsFirstPage_orderedByIdDesc() {
        val saved = (1..3).map { brandJpaRepository.save(Brand(BrandName("브랜드$it"))) }
        val expectedFirst = saved[2].id
        val expectedSecond = saved[1].id

        val window = brandJpaRepository.findByDeletedAtIsNull(ScrollPosition.keyset(), Limit.of(2), latestFirst)

        assertAll(
            { assertThat(window.content.map { it.id }).containsExactly(expectedFirst, expectedSecond) },
            { assertThat(window.hasNext()).isTrue() },
        )
    }

    @DisplayName("커서로 다음 페이지를 조회하면, 이전 페이지를 제외하고 이어진다.")
    @Test
    fun returnsNextPage_afterCursor() {
        val saved = (1..3).map { brandJpaRepository.save(Brand(BrandName("브랜드$it"))) }
        val cursor = saved[1].id
        val expectedLast = saved[0].id

        val window = brandJpaRepository.findByDeletedAtIsNull(
            ScrollPosition.of(mapOf<String, Any>("id" to cursor), ScrollPosition.Direction.FORWARD),
            Limit.of(2),
            latestFirst,
        )

        assertAll(
            { assertThat(window.content.map { it.id }).containsExactly(expectedLast) },
            { assertThat(window.hasNext()).isFalse() },
        )
    }

    @DisplayName("페이지 결과에서 soft delete된 브랜드는 제외된다.")
    @Test
    fun excludesDeletedBrand_fromPage() {
        brandJpaRepository.save(Brand(BrandName("나이키")))
        brandJpaRepository.save(Brand(BrandName("아디다스")).also { it.delete() })

        val window = brandJpaRepository.findByDeletedAtIsNull(ScrollPosition.keyset(), Limit.of(10), latestFirst)

        assertThat(window.content.map { it.name.value }).containsExactly("나이키")
    }
}

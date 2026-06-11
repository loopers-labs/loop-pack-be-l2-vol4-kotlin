package com.loopers.brand.infrastructure

import com.loopers.brand.domain.Brand
import com.loopers.brand.domain.BrandName
import com.loopers.brand.domain.BrandStatus
import com.loopers.shared.domain.IdCursor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class BrandRepositoryIntegrationTest @Autowired constructor(
    private val brandJpaRepository: BrandJpaRepository,
) {
    private val repository = BrandRepositoryImpl(brandJpaRepository)

    private fun deleted(name: String): Brand =
        Brand(BrandName(name)).also { it.transitionTo(BrandStatus.DELETED) }

    @DisplayName("ACTIVE 브랜드는 단건 조회로 찾을 수 있다.")
    @Test
    fun findsActiveBrand_whenSaved() {
        val saved = brandJpaRepository.save(Brand(BrandName("나이키")))

        assertThat(repository.findActiveById(saved.id)?.name).isEqualTo(BrandName("나이키"))
    }

    @DisplayName("DELETED 브랜드는 단건 조회로 찾을 수 없다.")
    @Test
    fun doesNotFindDeletedBrand() {
        val saved = brandJpaRepository.save(deleted("나이키"))

        assertThat(repository.findActiveById(saved.id)).isNull()
    }

    @DisplayName("이름 존재 여부를 조회한다. (삭제 포함 전체 행 대상)")
    @Test
    fun returnsExistsByName() {
        brandJpaRepository.save(Brand(BrandName("나이키")))

        assertAll(
            { assertThat(repository.existsByName(BrandName("나이키"))).isTrue() },
            { assertThat(repository.existsByName(BrandName("아디다스"))).isFalse() },
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

    @DisplayName("목록은 id DESC keyset으로 size만큼 반환하고, 다음 페이지가 있으면 nextCursor(IdCursor)를 채운다.")
    @Test
    fun returnsFirstPage_withNextCursor() {
        val saved = (1..3).map { brandJpaRepository.save(Brand(BrandName("브랜드$it"))) }

        val page = repository.findAll(null, 2)

        assertAll(
            { assertThat(page.content.map { it.id }).containsExactly(saved[2].id, saved[1].id) },
            { assertThat(page.hasNext).isTrue() },
            { assertThat(page.nextCursor).isEqualTo(IdCursor(saved[1].id)) },
        )
    }

    @DisplayName("nextCursor로 다음 페이지를 조회하면 이어지고, 마지막 페이지의 nextCursor는 null이다.")
    @Test
    fun returnsNextPage_andNullCursorOnLastPage() {
        val saved = (1..3).map { brandJpaRepository.save(Brand(BrandName("브랜드$it"))) }

        val lastPage = repository.findAll(IdCursor(saved[1].id), 2)

        assertAll(
            { assertThat(lastPage.content.map { it.id }).containsExactly(saved[0].id) },
            { assertThat(lastPage.hasNext).isFalse() },
            { assertThat(lastPage.nextCursor).isNull() },
        )
    }

    @DisplayName("목록 결과에서 DELETED 브랜드는 제외된다.")
    @Test
    fun excludesDeletedBrand_fromPage() {
        brandJpaRepository.save(Brand(BrandName("나이키")))
        brandJpaRepository.save(deleted("아디다스"))

        val page = repository.findAll(null, 10)

        assertThat(page.content.map { it.name.value }).containsExactly("나이키")
    }
}

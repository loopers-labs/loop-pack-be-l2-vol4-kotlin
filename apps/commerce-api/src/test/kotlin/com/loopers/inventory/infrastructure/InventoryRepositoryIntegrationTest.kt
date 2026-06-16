package com.loopers.inventory.infrastructure

import com.loopers.inventory.domain.Inventory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class InventoryRepositoryIntegrationTest @Autowired constructor(
    private val inventoryJpaRepository: InventoryJpaRepository,
) {
    private val repository = InventoryRepositoryImpl(inventoryJpaRepository)

    @DisplayName("productId로 활성 재고를 조회한다.")
    @Test
    fun findsActiveInventory_byProductId() {
        val saved = inventoryJpaRepository.save(Inventory.createFor(productId = 1L, quantity = 10))

        assertThat(repository.findByProductId(1L)?.id).isEqualTo(saved.id)
    }

    @DisplayName("archive(soft delete)된 재고는 productId 조회에서 제외된다.")
    @Test
    fun doesNotFindArchivedInventory() {
        inventoryJpaRepository.save(Inventory.createFor(productId = 1L, quantity = 10).also { it.delete() })

        assertThat(repository.findByProductId(1L)).isNull()
    }

    @DisplayName("여러 productId의 활성 재고를 일괄 조회하며, archive된 재고는 제외한다.")
    @Test
    fun findsAllActiveInventories_byProductIds() {
        inventoryJpaRepository.save(Inventory.createFor(1L, 10))
        inventoryJpaRepository.save(Inventory.createFor(2L, 20))
        inventoryJpaRepository.save(Inventory.createFor(3L, 30).also { it.delete() })

        val found = repository.findAllByProductIdIn(listOf(1L, 2L, 3L))

        assertThat(found.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
    }
}

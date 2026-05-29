package com.loopers.domain.inventory

import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InventoryTest {
    @DisplayName("재고를 생성할 때 수량이 0이면 허용된다.")
    @Test
    fun createsInventory_withZeroQuantity() {
        val inventory = Inventory.createFor(productId = 1L, quantity = 0)

        assertThat(inventory.quantity).isEqualTo(0)
    }

    @DisplayName("재고를 생성할 때 수량이 음수면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenCreatedWithNegativeQuantity() {
        val result = assertThrows<BadRequestException> {
            Inventory.createFor(productId = 1L, quantity = -1)
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.INVALID_QUANTITY)
    }

    @DisplayName("잔량과 같은 수량을 차감하면, 잔량이 0이 된다.")
    @Test
    fun decreasesToZero_whenAmountEqualsQuantity() {
        val inventory = Inventory.createFor(1L, 10)

        inventory.decrease(10)

        assertThat(inventory.quantity).isEqualTo(0)
    }

    @DisplayName("잔량보다 적은 수량을 차감하면, 남은 만큼 잔량이 줄어든다.")
    @Test
    fun decreasesQuantity_whenAmountLessThanQuantity() {
        val inventory = Inventory.createFor(1L, 10)

        inventory.decrease(3)

        assertThat(inventory.quantity).isEqualTo(7)
    }

    @DisplayName("잔량보다 많은 수량을 차감하면, CONFLICT(재고 부족) 예외가 발생한다.")
    @Test
    fun throwsConflict_whenAmountExceedsQuantity() {
        val inventory = Inventory.createFor(1L, 10)

        val result = assertThrows<ConflictException> {
            inventory.decrease(11)
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.STOCK_INSUFFICIENT)
    }

    @DisplayName("0 이하의 수량을 차감하면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenDecreaseAmountIsNotPositive() {
        val inventory = Inventory.createFor(1L, 10)

        val result = assertThrows<BadRequestException> {
            inventory.decrease(0)
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.INVALID_QUANTITY)
    }

    @DisplayName("수량을 증가하면, 잔량이 늘어난다.")
    @Test
    fun increasesQuantity() {
        val inventory = Inventory.createFor(1L, 10)

        inventory.increase(5)

        assertThat(inventory.quantity).isEqualTo(15)
    }

    @DisplayName("0 이하의 수량을 증가하면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenIncreaseAmountIsNotPositive() {
        val inventory = Inventory.createFor(1L, 10)

        val result = assertThrows<BadRequestException> {
            inventory.increase(-1)
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.INVALID_QUANTITY)
    }
}

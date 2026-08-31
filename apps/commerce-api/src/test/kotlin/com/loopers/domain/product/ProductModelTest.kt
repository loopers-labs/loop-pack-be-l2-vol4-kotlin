package com.loopers.domain.product
import com.loopers.support.error.CoreException;import org.assertj.core.api.Assertions.assertThat;import org.junit.jupiter.api.*;import org.junit.jupiter.api.assertThrows
class ProductModelTest{@Test fun acceptsZeroAndRejectsNegativePrice(){assertThat(ProductModel(7,"zero",0).price).isZero();assertThrows<CoreException>{ProductModel(7,"bad",-1)}}}

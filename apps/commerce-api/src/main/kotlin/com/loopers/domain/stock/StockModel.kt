package com.loopers.domain.stock
import com.loopers.domain.BaseEntity
import com.loopers.support.error.*
import jakarta.persistence.*
// Hides: non-negative stock invariants and decrement failure semantics.
@Entity @Table(name="stock") class StockModel(val productId:Long,var quantity:Int):BaseEntity(){init{if(productId<=0||quantity<0)throw CoreException(ErrorType.BAD_REQUEST)};fun decrease(amount:Int){if(amount<=0)throw CoreException(ErrorType.BAD_REQUEST);if(quantity<amount)throw CoreException(ErrorType.CONFLICT,"insufficient stock");quantity-=amount}}

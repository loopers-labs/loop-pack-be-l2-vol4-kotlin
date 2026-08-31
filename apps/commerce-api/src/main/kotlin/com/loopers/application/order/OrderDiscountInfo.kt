package com.loopers.application.order
import com.loopers.domain.order.Order
data class OrderDiscountInfo(val orderId:Long,val originalAmount:Long,val discountAmount:Long,val finalAmount:Long,val confirmed:Boolean){companion object{fun from(o:Order)=OrderDiscountInfo(o.id,o.originalAmount,o.discountAmount,o.finalAmount,o.confirmed)}}

package com.loopers.application.order
import com.loopers.domain.order.*
import com.loopers.support.error.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
// Hides: the transaction boundary and persistence sequencing for coupon application.
@Component class OrderDiscountFacade(private val repository:OrderRepository,private val service:OrderDiscountService){
 @Transactional fun apply(orderId:Long,buyerId:Long,couponId:Long,startedAt:Instant):OrderDiscountInfo{val o=repository.find(orderId)?:throw CoreException(ErrorType.NOT_FOUND,"order not found");service.apply(o,buyerId,couponId,startedAt);return OrderDiscountInfo.from(repository.save(o))}
}

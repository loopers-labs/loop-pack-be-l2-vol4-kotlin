package com.loopers.interfaces.api.order
import com.loopers.application.order.*
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.*
import java.time.Instant
@RestController @RequestMapping("/api/v1/orders") class OrderDiscountV1Controller(private val facade:OrderDiscountFacade){
 @PostMapping("/{orderId}/discount") fun apply(@PathVariable orderId:Long,@RequestHeader("X-USER-ID") buyerId:Long,@RequestBody request:Request)=ApiResponse.success(facade.apply(orderId,buyerId,request.couponId,Instant.now()))
 data class Request(val couponId:Long)
}

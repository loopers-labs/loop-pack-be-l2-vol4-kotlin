package com.loopers.interfaces.api.payment
import com.loopers.application.payment.PaymentFacade;import com.loopers.interfaces.api.ApiResponse;import org.springframework.web.bind.annotation.*
@RestController @RequestMapping("/api/v1/payments") class PaymentCallbackController(private val facade:PaymentFacade){@PostMapping("/callback")fun callback(@RequestBody r:Callback)=ApiResponse.success(facade.callback(r.orderId,r.transactionKey,r.status).status);data class Callback(val transactionKey:String,val orderId:String,val status:String)}

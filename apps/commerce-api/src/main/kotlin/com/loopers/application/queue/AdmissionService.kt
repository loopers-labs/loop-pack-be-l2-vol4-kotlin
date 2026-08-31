package com.loopers.application.queue
import com.loopers.infrastructure.queue.RedisAdmissionStore;import org.springframework.stereotype.Component
// Hides: capacity policy and fail-closed network failure semantics.
@Component class AdmissionService(private val store:RedisAdmissionStore){enum class Result{ACCEPTED,REJECTED};fun admit(id:String,capacity:Int):Result{require(id.isNotBlank()&&capacity>0);return try{if(store.admit(id,capacity))Result.ACCEPTED else Result.REJECTED}catch(_:RuntimeException){Result.REJECTED}};fun completed(id:String)=store.terminal(id);fun failed(id:String)=store.terminal(id)}

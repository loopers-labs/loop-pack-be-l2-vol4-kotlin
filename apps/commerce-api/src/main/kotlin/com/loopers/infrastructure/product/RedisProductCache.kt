package com.loopers.infrastructure.product
import com.loopers.application.product.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
// Hides: Redis key/serialization/TTL details behind the product result contract.
@Component class RedisProductCache(private val redis:StringRedisTemplate):ProductCache{private fun key(b:Long,l:Int)="w4:product:$b:$l";override fun get(brandId:Long,limit:Int)=redis.opsForValue().get(key(brandId,limit))?.split(',')?.filter{it.isNotBlank()}?.map{val p=it.split(':');ProductQueryService.Result(p[0].toLong(),p[1].toLong())};override fun put(brandId:Long,limit:Int,value:List<ProductQueryService.Result>){redis.opsForValue().set(key(brandId,limit),value.joinToString(","){"${it.id}:${it.price}"},Duration.ofMinutes(5))};override fun evict(brandId:Long){redis.keys("w4:product:$brandId:*")?.takeIf{it.isNotEmpty()}?.let{redis.delete(it)}}}

package com.loopers.application.product
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
// Hides: deterministic DB ordering, cache-aside fallback, and invalidation scope.
@Component class ProductQueryService(private val db:JdbcTemplate,private val cache:ProductCache){data class Result(val id:Long,val price:Long);fun find(brandId:Long,limit:Int):List<Result>{require(brandId>0&&limit>0);try{cache.get(brandId,limit)?.let{return it}}catch(_:RuntimeException){};val rows=db.query("select id,price from products where brand_id=? order by price,id limit ?",{rs,_->Result(rs.getLong(1),rs.getLong(2))},brandId,limit);try{cache.put(brandId,limit,rows)}catch(_:RuntimeException){};return rows};fun priceChanged(brandId:Long){try{cache.evict(brandId)}catch(_:RuntimeException){}}}

package com.loopers.application.product
interface ProductCache{fun get(brandId:Long,limit:Int):List<ProductQueryService.Result>?;fun put(brandId:Long,limit:Int,value:List<ProductQueryService.Result>);fun evict(brandId:Long)}

package com.loopers.interfaces.api.product
import com.loopers.application.product.ProductQueryService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.*
@RestController @RequestMapping("/api/v1/products") class ProductQueryV1Controller(private val service:ProductQueryService){@GetMapping fun query(@RequestParam brandId:Long,@RequestParam sort:String,@RequestParam limit:Int):ApiResponse<List<ProductQueryService.Result>>{require(sort=="price_asc");return ApiResponse.success(service.find(brandId,limit))}}

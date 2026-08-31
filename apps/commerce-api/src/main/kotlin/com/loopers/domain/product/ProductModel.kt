package com.loopers.domain.product
import com.loopers.domain.BaseEntity
import com.loopers.support.error.*
import jakarta.persistence.*
// Hides: product price and identity boundaries for the Kotlin learner track.
@Entity @Table(name="product") class ProductModel(val brandId:Long,val name:String,val price:Long):BaseEntity(){init{if(brandId<=0||name.isBlank()||price<0)throw CoreException(ErrorType.BAD_REQUEST)}}

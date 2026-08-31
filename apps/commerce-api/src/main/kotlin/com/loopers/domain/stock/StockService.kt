package com.loopers.domain.stock
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.support.error.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
// Hides: row-lock acquisition and the atomic stock transaction.
@Component class StockService(private val repository:StockJpaRepository){@Transactional fun decrease(id:Long,amount:Int){val s=repository.findLocked(id)?:throw CoreException(ErrorType.NOT_FOUND);s.decrease(amount)}}

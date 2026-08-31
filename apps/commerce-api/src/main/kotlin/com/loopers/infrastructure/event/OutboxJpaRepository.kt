package com.loopers.infrastructure.event
import com.loopers.domain.event.OutboxEvent;import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param
interface OutboxJpaRepository:JpaRepository<OutboxEvent,Long>{@Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select e from OutboxEvent e where e.id=:id")fun findLocked(@Param("id")id:Long):OutboxEvent?}

# Round 7 Step 3 (Plan C) — 선착순 쿠폰 발급 (Kafka)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 선착순 수량 한정 쿠폰 발급을 Kafka로 비동기화한다. API는 발급 요청을 접수(즉시 `requestId` 반환)만 하고, Consumer가 **원자적 수량 차감**으로 실제 발급하여 초과발급·중복발급을 방지한다. 결과는 폴링으로 확인한다.

**Architecture:** 요청 API → `coupon_issue_request(PENDING)` 저장 + B1 Outbox로 `coupon-issue-requests`(key=couponId) 발행(같은 tx). 릴레이가 Kafka 발행. **commerce-api의 `@KafkaListener`**(coupon-issue-requests, key=couponId → 쿠폰별 단일 파티션 순차)가 소비 → 원자적 `UPDATE coupons SET issued_count=issued_count+1 WHERE issued_count<total_quantity` → 성공 시 `UserCoupon` 발급 + 요청 `ISSUED`, 초과 시 `REJECTED(SOLD_OUT)`. 요청 상태(terminal→skip)와 `(user_id,coupon_id)` 유니크가 멱등/중복 방지.

**결정(기본값, 리뷰서 재검토 가능):** Consumer는 **commerce-api**에 호스팅(쿠폰 도메인이 api에 있음 → 최단, 신규 크로스앱 결합 없음, api는 이미 Kafka 배선됨). 대안: commerce-streamer가 commerce-api 의존(batch 선례) — 앱 분리 강화하나 결합↑. 비동기 분리(즉시 응답)는 이 구조로도 시연됨.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, spring-kafka(`BATCH_LISTENER`), JPA(원자적 JPQL UPDATE), B1 Outbox 재사용, `runConcurrently` 동시성 헬퍼, Testcontainers(MySQL + Kafka).

## Global Constraints

- 레이어: `domain/coupon`(엔티티·포트), `infrastructure/coupon`(JPA·어댑터), `application/coupon`(usecase·command), `interfaces/api/coupon`(controller)·`interfaces/consumer`(리스너).
- **선착순 수량**: `CouponModel`에 `total_quantity: Int?`(null=무제한, 기존 쿠폰 호환), `issued_count: Int=0`. 슬롯 확보는 **원자적 조건부 UPDATE**(`issued_count < total_quantity`), affected==1만 성공.
- 요청 접수/발급 **트랜잭션 분리**: 요청 API는 `coupon_issue_request(PENDING)` + Outbox만(즉시 응답). 발급은 Consumer가 별도 tx.
- **멱등/중복방지**: 요청 상태 terminal(ISSUED/REJECTED)이면 재처리 skip. `user_coupons (user_id,coupon_id)` 유니크로 1인 1매. 초과발급 0은 원자적 UPDATE + 쿠폰별 단일 파티션 순차.
- 인증: 기존과 동일 `X-Loopers-LoginId`/`X-Loopers-LoginPw` 헤더 → `UserService.getProfile`.
- B1 자산 재사용: `OutboxEventCaptureListener`(BEFORE_COMMIT)가 `OutboxMessageFactory.from`으로 매핑 — 신규 이벤트를 factory에 추가하면 자동 캡처. `OutboxRelay`가 발행.
- 메시지 계약: `coupon-issue-requests`(key=couponId): `{"eventId":str,"type":"COUPON_ISSUE_REQUESTED","requestId":str,"userId":long,"couponId":long,"occurredAt":str}`.
- ktlint(≤130). 커밋 말미 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: 쿠폰 선착순 수량 + 원자적 슬롯 확보

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponModel.kt` (add `totalQuantity: Int?`, `issuedCount: Int`)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponRepository.kt` (add `claimIssueSlot(couponId): Boolean`)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponJpaRepository.kt` (atomic `@Modifying` UPDATE)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponRepositoryImpl.kt` (implement `claimIssueSlot`)
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/coupon/CouponIssueSlotConcurrencyTest.kt`

**Interfaces:**
- `CouponModel(name, type, discountValue, minOrderAmount, expiredAt, totalQuantity: Int? = null, issuedCount: Int = 0)` — 새 필드는 끝에 기본값(기존 호출부 호환).
- `CouponRepository.claimIssueSlot(couponId: Long): Boolean` — 원자적 UPDATE affected==1이면 true(슬롯 확보), 매진/미존재면 false.

- [ ] **Step 1: 실패 테스트 작성** — `CouponIssueSlotConcurrencyTest.kt` (MySQL 컨테이너; N개 슬롯에 M>N 동시 확보 → 정확히 N):

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class CouponIssueSlotConcurrencyTest {
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("총 수량 N개 쿠폰에 M(>N)개 동시 슬롯 확보 요청이 와도 정확히 N개만 성공한다.")
    @Test
    fun claimsExactlyTotalQuantityUnderConcurrency() {
        // arrange
        val total = 100
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순", type = CouponType.FIXED, discountValue = BigDecimal("1000"),
                minOrderAmount = null, expiredAt = ZonedDateTime.now().plusDays(1),
                totalQuantity = total,
            ),
        )

        // act
        val successes = java.util.concurrent.atomic.AtomicInteger(0)
        runConcurrently(threadCount = 300) {
            if (couponRepository.claimIssueSlot(coupon.id)) successes.incrementAndGet()
        }

        // assert
        assertThat(successes.get()).isEqualTo(total)
        assertThat(couponRepository.findActiveById(coupon.id)?.issuedCount).isEqualTo(total)
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.coupon.CouponIssueSlotConcurrencyTest"` → FAIL(미해결/초과).

- [ ] **Step 3: 구현**

`CouponModel.kt` — 생성자 끝에 필드 추가(기존 호출부 무변경):
```kotlin
class CouponModel(
    name: String,
    type: CouponType,
    discountValue: BigDecimal,
    minOrderAmount: BigDecimal?,
    expiredAt: ZonedDateTime,
    totalQuantity: Int? = null,
    issuedCount: Int = 0,
) : BaseEntity() {
    // ... 기존 필드들 ...

    @Column(name = "total_quantity")
    var totalQuantity: Int? = totalQuantity
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Int = issuedCount
        protected set
    // ... 기존 메서드들 ...
}
```

`CouponRepository.kt` — 포트 추가:
```kotlin
    /** 선착순 슬롯 확보: issued_count < total_quantity 일 때만 원자적으로 +1. 성공 시 true. */
    fun claimIssueSlot(couponId: Long): Boolean
```

`CouponJpaRepository.kt` — 원자적 UPDATE(JPQL). `clearAutomatically`로 영속성 컨텍스트 정리(이후 재조회 정합):
```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE CouponModel c SET c.issuedCount = c.issuedCount + 1
        WHERE c.id = :couponId
          AND c.deletedAt IS NULL
          AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)
        """,
    )
    fun claimIssueSlot(@Param("couponId") couponId: Long): Int
```

`CouponRepositoryImpl.kt` — 어댑터:
```kotlin
    override fun claimIssueSlot(couponId: Long): Boolean = couponJpaRepository.claimIssueSlot(couponId) == 1
```

- [ ] **Step 4: 통과 확인** — test PASS (정확히 100).

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponModel.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponJpaRepository.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponRepositoryImpl.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/coupon/CouponIssueSlotConcurrencyTest.kt
git commit -m "feat: coupon total_quantity + atomic issue-slot claim (R7-C)"
```

---

### Task 2: 발급 요청 접수 (요청 저장 + Outbox 발행) + 결과 폴링 API

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssueRequest.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssueStatus.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssueRequestRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssueRequestedEvent.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponIssueRequestJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponIssueRequestRepositoryImpl.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/RequestCouponIssueUsecase.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/GetCouponIssueResultUsecase.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/OutboxMessageFactory.kt` (add `CouponIssueRequestedEvent` → coupon-issue-requests)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/KafkaTopics.kt` (add `COUPON_ISSUE_REQUESTS`)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/CouponV1Controller.kt` (+DTO) — POST 요청, GET 결과
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/RequestCouponIssueUsecaseIntegrationTest.kt`

**Interfaces:**
- `CouponIssueRequest(requestId: String, userId, couponId)` extends BaseEntity; `status=PENDING`, `reason: String?`; `markIssued()`, `markRejected(reason)`.
- `CouponIssueRequestRepository { save; findByRequestId(requestId): CouponIssueRequest? }`.
- `CouponIssueRequestedEvent(requestId, userId, couponId)`.
- `RequestCouponIssueUsecase.execute(loginId, password, couponId): String`(requestId) — 인증·쿠폰 유효성 → 요청 저장 + 이벤트 발행.
- `GetCouponIssueResultUsecase.execute(requestId): CouponIssueResult(requestId, status, reason)`.

- [ ] **Step 1: 실패 테스트 작성** — `RequestCouponIssueUsecaseIntegrationTest.kt` (요청 커밋 → coupon_issue_request(PENDING) + outbox 행 생성):

```kotlin
package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.RequestCouponIssueUsecase
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
// (user 저장은 기존 통합테스트 방식 재사용)
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class RequestCouponIssueUsecaseIntegrationTest {
    @Autowired lateinit var requestUsecase: RequestCouponIssueUsecase
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var requestRepository: CouponIssueRequestRepository
    @Autowired lateinit var outboxRepository: OutboxEventRepository
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("발급 요청을 접수하면 PENDING 요청과 coupon-issue-requests outbox 행이 같은 tx로 생성된다.")
    @Test
    fun acceptsRequestAndEnqueuesOutbox() {
        // arrange: 활성 유저 + 선착순 쿠폰 (user 셋업은 기존 통합테스트 패턴 재사용)
        val loginId = "loopers01"; val password = "Pass1234!"
        // userService.signUp(...) 등으로 유저 생성 — 기존 테스트 방식 그대로
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순", type = CouponType.FIXED, discountValue = BigDecimal("1000"),
                minOrderAmount = null, expiredAt = ZonedDateTime.now().plusDays(1), totalQuantity = 100,
            ),
        )

        // act
        val requestId = requestUsecase.execute(loginId, password, coupon.id)

        // assert
        val req = requestRepository.findByRequestId(requestId)
        assertThat(req?.status).isEqualTo(CouponIssueStatus.PENDING)
        val pending = outboxRepository.findTopPending(10)
        assertThat(pending).anyMatch { it.topic == KafkaTopics.COUPON_ISSUE_REQUESTS && it.partitionKey == coupon.id.toString() }
    }
}
```
> user 생성은 이 모듈 기존 통합테스트(`IssueCouponUsecaseTest`/`OrderCouponIntegrationTest`)의 방식을 그대로 따른다.

- [ ] **Step 2: 실패 확인** — FAIL(미해결).

- [ ] **Step 3: 구현**

`CouponIssueStatus.kt`:
```kotlin
package com.loopers.domain.coupon

enum class CouponIssueStatus { PENDING, ISSUED, REJECTED }
```

`CouponIssueRequest.kt`:
```kotlin
package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "coupon_issue_requests",
    uniqueConstraints = [UniqueConstraint(name = "uk_coupon_issue_request_id", columnNames = ["request_id"])],
)
class CouponIssueRequest(
    requestId: String,
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "request_id", nullable = false, updatable = false, length = 36)
    var requestId: String = requestId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CouponIssueStatus = CouponIssueStatus.PENDING
        protected set

    @Column(name = "reason", length = 100)
    var reason: String? = null
        protected set

    fun markIssued() { status = CouponIssueStatus.ISSUED; reason = null }
    fun markRejected(reason: String) { status = CouponIssueStatus.REJECTED; this.reason = reason }
    fun isTerminal(): Boolean = status != CouponIssueStatus.PENDING
}
```

`CouponIssueRequestedEvent.kt`:
```kotlin
package com.loopers.domain.coupon

data class CouponIssueRequestedEvent(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
)
```

`CouponIssueRequestRepository.kt` / JPA / Impl (Like 패턴):
```kotlin
// domain
interface CouponIssueRequestRepository {
    fun save(request: CouponIssueRequest): CouponIssueRequest
    fun findByRequestId(requestId: String): CouponIssueRequest?
}
// infrastructure
interface CouponIssueRequestJpaRepository : org.springframework.data.jpa.repository.JpaRepository<CouponIssueRequest, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequest?
}
@org.springframework.stereotype.Component
class CouponIssueRequestRepositoryImpl(private val jpa: CouponIssueRequestJpaRepository) : CouponIssueRequestRepository {
    override fun save(request: CouponIssueRequest) = jpa.save(request)
    override fun findByRequestId(requestId: String) = jpa.findByRequestId(requestId)
}
```

`KafkaTopics.kt` — 상수 추가: `const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"`.

`OutboxMessageFactory.kt` — `when`에 분기 추가:
```kotlin
is CouponIssueRequestedEvent -> {
    val eventId = UUID.randomUUID().toString()
    val payload = objectMapper.writeValueAsString(
        linkedMapOf(
            "eventId" to eventId,
            "type" to "COUPON_ISSUE_REQUESTED",
            "requestId" to event.requestId,
            "userId" to event.userId,
            "couponId" to event.couponId,
            "occurredAt" to ZonedDateTime.now().toString(),
        ),
    )
    OutboxDraft(eventId, KafkaTopics.COUPON_ISSUE_REQUESTS, event.couponId.toString(), payload)
}
```

`RequestCouponIssueUsecase.kt`:
```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueRequestedEvent
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@Component
class RequestCouponIssueUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val requestRepository: CouponIssueRequestRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // 접수만: 쿠폰 유효성 확인 후 PENDING 요청 저장 + Outbox 발행(같은 tx). 발급은 Consumer가 비동기 수행.
    @Transactional
    fun execute(loginId: String, password: String, couponId: Long): String {
        val user = userService.getProfile(loginId = loginId, password = password)
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        if (coupon.isExpired(ZonedDateTime.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.")
        }
        val requestId = UUID.randomUUID().toString()
        requestRepository.save(CouponIssueRequest(requestId = requestId, userId = user.id, couponId = couponId))
        eventPublisher.publishEvent(CouponIssueRequestedEvent(requestId = requestId, userId = user.id, couponId = couponId))
        return requestId
    }
}
```

`GetCouponIssueResultUsecase.kt`:
```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CouponIssueResult(val requestId: String, val status: CouponIssueStatus, val reason: String?)

@Component
class GetCouponIssueResultUsecase(
    private val requestRepository: CouponIssueRequestRepository,
) {
    @Transactional(readOnly = true)
    fun execute(requestId: String): CouponIssueResult {
        val req = requestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.")
        return CouponIssueResult(req.requestId, req.status, req.reason)
    }
}
```

`CouponV1Controller.kt` — 엔드포인트 2개 추가(+DTO). 요청은 `202`-성격이나 기존 `ApiResponse.success` 유지:
```kotlin
@PostMapping("/api/v1/coupons/{couponId}/issue-requests")
fun requestIssue(
    @RequestHeader("X-Loopers-LoginId") loginId: String,
    @RequestHeader("X-Loopers-LoginPw") password: String,
    @PathVariable couponId: Long,
): ApiResponse<CouponV1Dto.IssueRequestResponse> =
    requestCouponIssueUsecase.execute(loginId, password, couponId)
        .let { ApiResponse.success(CouponV1Dto.IssueRequestResponse(requestId = it, status = "PENDING")) }

@GetMapping("/api/v1/coupons/issue-requests/{requestId}")
fun issueResult(@PathVariable requestId: String): ApiResponse<CouponV1Dto.IssueResultResponse> =
    getCouponIssueResultUsecase.execute(requestId)
        .let { ApiResponse.success(CouponV1Dto.IssueResultResponse(it.requestId, it.status.name, it.reason)) }
```
> `CouponV1Dto`에 `IssueRequestResponse(requestId, status)`, `IssueResultResponse(requestId, status, reason)` 추가. 컨트롤러 생성자에 두 usecase 주입.

- [ ] **Step 4: 통과 확인** — test PASS.

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssue*.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponIssueRequestedEvent.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponIssueRequest*.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/RequestCouponIssueUsecase.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/GetCouponIssueResultUsecase.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/outbox/OutboxMessageFactory.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/domain/outbox/KafkaTopics.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/CouponV1Controller.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/CouponV1Dto.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/RequestCouponIssueUsecaseIntegrationTest.kt
git commit -m "feat: coupon issue request accept + outbox publish + result polling (R7-C)"
```

---

### Task 3: 발급 처리(원자적) + 동시성 검증

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/IssueCouponFromRequestUsecase.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/IssueCouponFromRequestConcurrencyTest.kt`

**Interfaces:**
- `IssueCouponFromRequestUsecase.issue(requestId, userId, couponId)` — @Transactional. 요청 terminal이면 skip; 이미 발급 보유면 ISSUED(멱등); 아니면 `claimIssueSlot`→성공: `UserCoupon` 저장 + 요청 ISSUED, 실패: 요청 REJECTED(SOLD_OUT). `UserCoupon` 유니크 위반(동시 중복)은 catch → 요청 ISSUED(이미 보유).

- [ ] **Step 1: 실패 테스트 작성** — `IssueCouponFromRequestConcurrencyTest.kt` (직접 동시 호출 — Kafka 없이 원자적 발급 검증. 각 스레드 distinct userId+requestId, 같은 couponId, total=N → 정확히 N 발급):

```kotlin
package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.IssueCouponFromRequestUsecase
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class IssueCouponFromRequestConcurrencyTest {
    @Autowired lateinit var issueUsecase: IssueCouponFromRequestUsecase
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var userCouponRepository: UserCouponRepository
    @Autowired lateinit var requestRepository: CouponIssueRequestRepository
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("총 N개 쿠폰에 M(>N)명이 동시에 발급 처리돼도 정확히 N명만 ISSUED, 나머지는 REJECTED(SOLD_OUT), 초과발급 0.")
    @Test
    fun issuesExactlyTotalQuantityUnderConcurrency() {
        // arrange
        val total = 100
        val users = 300
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순", type = CouponType.FIXED, discountValue = BigDecimal("1000"),
                minOrderAmount = null, expiredAt = ZonedDateTime.now().plusDays(1), totalQuantity = total,
            ),
        )
        val requestIds = (0 until users).map { "req-$it" }
        requestIds.forEachIndexed { i, rid ->
            requestRepository.save(CouponIssueRequest(requestId = rid, userId = (i + 1).toLong(), couponId = coupon.id))
        }

        // act
        runConcurrently(threadCount = users) { i ->
            issueUsecase.issue(requestId = requestIds[i], userId = (i + 1).toLong(), couponId = coupon.id)
        }

        // assert
        val issued = requestIds.count { requestRepository.findByRequestId(it)?.status == CouponIssueStatus.ISSUED }
        assertThat(issued).isEqualTo(total)
        assertThat(userCouponRepository.findAllByCouponId(coupon.id)).hasSize(total)
        assertThat(couponRepository.findActiveById(coupon.id)?.issuedCount).isEqualTo(total)
    }
}
```
> `UserCouponRepository.findAllByCouponId` 는 기존 포트에 존재(탐색 확인). 없으면 count 쿼리로 대체.

- [ ] **Step 2: 실패 확인** — FAIL(미해결/초과).

- [ ] **Step 3: 구현** — `IssueCouponFromRequestUsecase.kt`:
```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssueCouponFromRequestUsecase(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val requestRepository: CouponIssueRequestRepository,
) {
    @Transactional
    fun issue(requestId: String, userId: Long, couponId: Long) {
        val request = requestRepository.findByRequestId(requestId) ?: return
        if (request.isTerminal()) return // 멱등: 이미 처리된 요청 재수신

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            request.markIssued() // 이미 보유 — 멱등 종결
            return
        }
        if (!couponRepository.claimIssueSlot(couponId)) {
            request.markRejected("SOLD_OUT")
            return
        }
        try {
            userCouponRepository.save(UserCouponModel(userId = userId, couponId = couponId))
            request.markIssued()
        } catch (e: DataIntegrityViolationException) {
            // 사전 체크를 통과한 동시 중복 — 슬롯은 이미 차감됨(과다 차감 감수), 요청은 보유로 종결
            request.markIssued()
        }
    }
}
```
> 주의: 쿠폰별 단일 파티션(key=couponId) 순차 처리에선 위 동시 중복이 발생하지 않는다. `runConcurrently` 직접 호출 테스트는 파티션 직렬화가 없는 최악 조건이라, 원자적 `claimIssueSlot`이 초과발급을 막는지 검증한다. (파티션 순차는 실제 파이프라인의 추가 방어.)
> ponytail: dup catch에서 슬롯 과다 차감 가능 — 순차 파이프라인에선 도달 불가라 감수. 필요 시 claim 이전에 락/재확인.

- [ ] **Step 4: 통과 확인** — test PASS (정확히 100 ISSUED, 200 REJECTED, issued_count=100).

- [ ] **Step 5: 커밋**
```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/IssueCouponFromRequestUsecase.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/IssueCouponFromRequestConcurrencyTest.kt
git commit -m "feat: atomic coupon issuance from request + concurrency proof (R7-C)"
```

---

### Task 4: Kafka Consumer(@KafkaListener) + end-to-end

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/consumer/CouponIssueConsumer.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/consumer/CouponIssueE2EIntegrationTest.kt`

**Interfaces:**
- `CouponIssueConsumer` — `@KafkaListener(topics=["coupon-issue-requests"], containerFactory=KafkaConfig.BATCH_LISTENER)`, `List<ConsumerRecord<String,ByteArray>>` + `Acknowledgment`. 각 레코드 JSON 파싱 → `IssueCouponFromRequestUsecase.issue`. per-record runCatching(격리) → ack.

- [ ] **Step 1: 실패 테스트 작성** — `CouponIssueE2EIntegrationTest.kt` (요청 API 대신 이벤트 발행→소비 왕복; MySQL+Kafka. 발행 → 소비 처리 → 요청 ISSUED 폴링):

```kotlin
package com.loopers.interfaces.consumer

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.ZonedDateTime

@TestPropertySource(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@SpringBootTest
class CouponIssueE2EIntegrationTest {
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var requestRepository: CouponIssueRequestRepository
    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp
    @Value("\${spring.kafka.bootstrap-servers}") lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("coupon-issue-requests 를 소비하면 요청이 ISSUED 로 종결된다.")
    @Test
    fun consumesAndIssues() {
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순", type = CouponType.FIXED, discountValue = BigDecimal("1000"),
                minOrderAmount = null, expiredAt = ZonedDateTime.now().plusDays(1), totalQuantity = 10,
            ),
        )
        requestRepository.save(CouponIssueRequest(requestId = "req-e2e", userId = 1L, couponId = coupon.id))
        publish(
            coupon.id.toString(),
            """{"eventId":"ev1","type":"COUPON_ISSUE_REQUESTED","requestId":"req-e2e","userId":1,"couponId":${coupon.id}}""",
        )

        var status: CouponIssueStatus? = null
        var tries = 0
        while (status != CouponIssueStatus.ISSUED && tries < 50) {
            Thread.sleep(200); status = requestRepository.findByRequestId("req-e2e")?.status; tries++
        }
        assertThat(status).isEqualTo(CouponIssueStatus.ISSUED)
    }

    private fun publish(key: String, value: String) {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(KafkaTopics.COUPON_ISSUE_REQUESTS, key, value)).get() }
    }
}
```

- [ ] **Step 2: 실패 확인** — FAIL(컨슈머 없음 → 계속 PENDING → timeout).

- [ ] **Step 3: 구현** — `CouponIssueConsumer.kt` (payload 파싱은 ObjectMapper로 requestId/userId/couponId 추출):
```kotlin
package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.usecase.IssueCouponFromRequestUsecase
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueConsumer(
    private val objectMapper: ObjectMapper,
    private val issueUsecase: IssueCouponFromRequestUsecase,
) {
    private val log = LoggerFactory.getLogger(CouponIssueConsumer::class.java)

    @KafkaListener(
        topics = ["coupon-issue-requests"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(records: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        records.forEach { record ->
            runCatching {
                val node = objectMapper.readTree(String(record.value(), Charsets.UTF_8))
                issueUsecase.issue(
                    requestId = node["requestId"].asText(),
                    userId = node["userId"].asLong(),
                    couponId = node["couponId"].asLong(),
                )
            }.onFailure {
                log.error(
                    "Failed to process coupon-issue record. partition={} offset={} payload={}",
                    record.partition(), record.offset(), String(record.value(), Charsets.UTF_8), it,
                )
            }
        }
        acknowledgment.acknowledge()
    }
}
```
> commerce-api가 이제 프로듀서 + 컨슈머 둘 다. 컨슈머 메서드는 @Transactional 아님(각 issue가 자체 tx).

- [ ] **Step 4: 통과 확인** — Docker(MySQL+Kafka). test PASS.

- [ ] **Step 5: 전체 회귀 + 커밋 + .http**
```bash
./gradlew :apps:commerce-api:test -q
./gradlew :apps:commerce-api:ktlintCheck -q
# http/commerce-api/coupon-issue-v1.http 에 POST issue-requests / GET result 예시 추가
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/consumer/CouponIssueConsumer.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/interfaces/consumer/CouponIssueE2EIntegrationTest.kt \
        http/commerce-api/coupon-issue-v1.http
git commit -m "feat: coupon-issue kafka consumer + e2e (R7-C)"
```

---

## Step 3 완료 기준 (DoD)

- 선착순 수량 한정 쿠폰: 발급 요청 API(즉시 requestId) → Kafka → Consumer가 원자적 수량 차감으로 발급.
- **초과발급 0 / 중복발급 0** — 동시성 테스트로 증명(정확히 N ISSUED).
- 결과 폴링 API로 유저가 ISSUED/REJECTED 확인.
- `./gradlew :apps:commerce-api:test`, `:apps:commerce-api:ktlintCheck` 통과.

## Self-Review (spec 대비)

- **API는 발행만, Consumer가 발급(§8)** → Task 2(요청+Outbox), Task 4(Consumer). ✅
- **발급 수량 제한 동시성 제어** → Task 1(원자적 claimIssueSlot) + Task 3(동시성 증명). ✅
- **중복 발급 방지** → user_coupons 유니크 + 요청 terminal 멱등(Task 3). ✅
- **결과 확인(polling)** → Task 2 GET. ✅
- **단일 파티션 순차(key=couponId)** → Task 2 발행 key + BATCH_LISTENER. ✅
- 타입 일관성: `CouponIssueRequestedEvent`(Task2 발행) = OutboxMessageFactory 매핑(Task2) = 메시지 계약 = 컨슈머 파싱(Task4). `claimIssueSlot`(Task1) = 소비(Task3).
- 미커버(의도적): admin 쿠폰 생성 시 total_quantity 입력 UI(테스트는 직접 생성) — 필요 시 별도. DLQ = Nice-to-have.

## 리스크 / 주의

- **Consumer = commerce-api**(결정): api가 프로듀서+컨슈머. 앱 분리 원하면 streamer+api의존으로 이동(batch 선례).
- **동시 중복 catch 시 슬롯 과다 차감**: 쿠폰별 단일 파티션 순차에선 도달 불가. `runConcurrently` 직접 테스트는 그 방어가 없는 최악 조건 — 원자적 claim이 초과발급 0 보장함을 검증(단, dup catch 경로의 슬롯 과다차감은 순차 파이프라인 전제로 감수. ponytail 주석).
- **admin 쿠폰 생성**: 기존 admin 생성 경로는 total_quantity 미설정(null=무제한). 선착순 쿠폰은 테스트/직접 생성. admin DTO 확장은 범위 밖.
- **@KafkaListener always-on**: commerce-api @SpringBootTest도 이제 Kafka 컨테이너 기동(B1 testFixtures 스캔). 통합테스트 폴링 대기.
- Docker 필요(Task 1/2/3/4 통합·동시성 테스트).

## 다음
Step 3 완료 → Round 7 전체(Step 1·2·3) 완료. PR #107 최종 반영. (필요 시 analyze-concurrency/analyze-external-integration로 재점검.)

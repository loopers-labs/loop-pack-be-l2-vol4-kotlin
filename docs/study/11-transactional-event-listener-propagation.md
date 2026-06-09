# @TransactionalEventListener + @Transactional propagation 검증

> 대상: Spring Framework 6.2.x (본 프로젝트는 Spring Boot 3.4.4 사용)
> 1차 목표: "AFTER_COMMIT 리스너에 @Transactional을 붙이면 propagation을 REQUIRES_NEW/NOT_SUPPORTED로 해야 한다. 아니면 컨텍스트 초기화 시점에 예외가 난다"는 주장의 사실 검증.

## 1. 한 줄 결론

**검증됨 (정정 포함).** `@TransactionalEventListener` 메서드에 `@Transactional`을 함께 붙이면, propagation은 `REQUIRES_NEW` 또는 `NOT_SUPPORTED`여야 하며, 그 외(`REQUIRED` 등)는 애플리케이션 컨텍스트 초기화 단계에서 `RestrictedTransactionalEventListenerFactory`가 `IllegalStateException`을 던집니다.

- **정정 1**: 원 주장은 "REQUIRES_NEW(또는 NOT_SUPPORTED)"라고 이미 둘 다 적었으나, 둘의 허용 시점이 다릅니다. `REQUIRES_NEW`만 처음 허용(이슈 #31414)됐고, `NOT_SUPPORTED`는 나중에 추가(이슈 #31907, PR #33470)됐습니다. [출처 확인]
- **정정 2 (버전)**: 원 주장은 "Spring Framework 6.1+ / Spring Boot 3.2+"라고 했으나, `RestrictedTransactionalEventListenerFactory`에 의한 **부팅 시점 검증(IllegalStateException)** 자체는 **6.2부터** 도입됐습니다. 6.1은 "transactional event listener가 thread-bound/reactive 트랜잭션과 동작"하도록 정리된 버전이지, 이 fail-fast 검증이 들어간 버전이 아닙니다. 본 프로젝트(Boot 3.4.4 → Framework 6.2.x)에는 검증이 적용됩니다. [출처 확인 — 단, 6.2.0 정확한 마일스톤은 검색 요약 기반]

근거 신뢰도 표기: 공식 reference/Javadoc 본문은 [출처 확인], `RestrictedTransactionalEventListenerFactory`의 **예외 메시지 원문**과 정확한 도입 마일스톤은 github.com 차단으로 직접 인용 불가 → [차단]/[검색 요약]으로 표기.

## 2. 왜 REQUIRED가 안 되는가 (메커니즘)

근거 가설이 맞습니다. 공식 `TransactionalEventListener` Javadoc의 WARNING이 핵심입니다. [출처 확인]

> **WARNING:** if the `TransactionPhase` is set to `AFTER_COMMIT` (the default), `AFTER_ROLLBACK`, or `AFTER_COMPLETION`, the transaction will have been committed or rolled back already, but the transactional resources might still be active and accessible. As a consequence, any data access code triggered at this point will still "participate" in the original transaction, but changes will not be committed to the transactional resource.

해석:

- `AFTER_COMMIT` 시점에는 원본 트랜잭션이 **이미 커밋·완료된 상태**입니다. 그러나 thread-bound 리소스(JPA `EntityManager`, JDBC `Connection` 등)는 `afterCompletion` 콜백이 끝날 때까지 아직 thread에 매여 있습니다.
- 이 시점에서 `Propagation.REQUIRED`(기본값)로 `@Transactional` 메서드를 호출하면, Spring은 "기존 트랜잭션에 **참여(join)**"하려고 합니다. 그런데 참여 대상 트랜잭션은 이미 커밋이 끝난 상태입니다.
- 결과: 그 안의 데이터 변경은 트랜잭션 리소스에 **커밋되지 않습니다(silent no-op)**. 개발자는 "AFTER_COMMIT에서 DB에 썼다"고 믿지만 실제로는 반영되지 않는, 진단하기 어려운 데이터 유실이 발생합니다.

이 함정을 부팅 시점에 차단하기 위해 Spring이 `REQUIRED`를 거부합니다.

- `REQUIRES_NEW`: 현재(완료 중인) 트랜잭션을 suspend하고 **새 트랜잭션을 시작** → 리스너 안의 쓰기가 독립적으로 커밋됩니다.
- `NOT_SUPPORTED`: 현재 트랜잭션을 suspend하고 **비트랜잭션으로 실행** → 트랜잭션이 필요 없는 작업(예: 외부 호출, 멱등 카운터)에 적합.

Propagation Javadoc 원문 [출처 확인]:

- `REQUIRES_NEW` — "Create a new transaction, and suspend the current transaction if one exists."
- `NOT_SUPPORTED` — "Execute non-transactionally, suspend the current transaction if one exists."

둘 다 "현재 트랜잭션을 suspend"한다는 공통점이 있어, "완료 중인 원본 트랜잭션에 join하지 않는다"는 invariant를 만족합니다. 그래서 이 둘만 허용됩니다.

## 3. RestrictedTransactionalEventListenerFactory — 던지는 조건과 메시지

### 3.1 클래스 성격

- `RestrictedTransactionalEventListenerFactory`는 `TransactionalEventListenerFactory`(since 4.2)의 서브클래스입니다. [출처 확인 — Javadoc package/superclass 관계]
- **공개 Javadoc 페이지가 존재하지 않습니다**(current/6.2.x 모든 버전 URL에서 404, 현재 7.0 package-summary 목록에도 미등재). 즉 사실상 internal 클래스로, Spring이 내부적으로 등록합니다. → 그래서 **예외 메시지 원문을 공식 페이지에서 직접 인용 불가**. [차단]

### 3.2 검증 조건 (요약된 동작)

검색으로 수집된 클래스 설명(요약): "an extension of `TransactionalEventListenerFactory` that detects invalid transaction configuration for transactional event listeners: `@Transactional` is only supported with `Propagation.REQUIRES_NEW` and `Propagation.NOT_SUPPORTED`." [검색 요약]

정리하면:

- 트리거: 같은 메서드에 `@TransactionalEventListener` + `@Transactional`이 동시에 있고, `@Transactional`의 propagation이 `REQUIRES_NEW`도 `NOT_SUPPORTED`도 아닐 때.
- 메서드/클래스 레벨 `@Transactional` 모두를 검사합니다 (PR #33470). [검색 요약]
- 예외 타입: `IllegalStateException`. [검색 요약 — 이슈 #30679/#31414/PR #33470 일관]
- 발생 시점: 빈 생성/리스너 어댑터 생성 단계, 즉 **애플리케이션 컨텍스트 초기화 시점**. fail-fast이므로 런타임 이벤트 발행 전에 기동이 실패합니다.
- **예외 메시지 원문**: github.com 소스/이슈 차단으로 글자 단위 확정 불가. [차단] 의미는 "`@TransactionalEventListener` 메서드의 `@Transactional`은 `REQUIRES_NEW` 또는 `NOT_SUPPORTED` propagation으로만 선언될 수 있다"는 취지입니다.

### 3.3 언제 이 팩토리가 활성화되는가

- `@EnableTransactionManagement`가 활성화되면, 기본 `TransactionalEventListenerFactory`가 `RestrictedTransactionalEventListenerFactory`로 **교체 등록**됩니다. [검색 요약 — 이슈 #32319]
- Spring Boot는 데이터 액세스 기술(`PlatformTransactionManager`가 classpath에 존재)이 있으면 자동으로 트랜잭션 관리를 활성화하므로, 일반적인 Boot 앱에서는 이 팩토리가 자동으로 쓰입니다. (`TransactionAutoConfiguration`은 `PlatformTransactionManager`가 classpath에 있을 때 트리거됨 — Spring Boot Javadoc) [출처 확인 — 개념]
- 반대로 `@EnableTransactionManagement`가 없으면 `DefaultEventListenerFactory`가 `@EventListener` 메타애너테이션만 보고 일반 리스너로 처리 → 검증이 안 걸리고 트랜잭션 바인딩도 안 됨 (이슈 #32319의 버그 맥락). [검색 요약]

## 4. 버전 타임라인 (이슈/PR 기준)

| 단계 | 내용 | 출처 | 신뢰도 |
|---|---|---|---|
| #30679 | "transactional event listener의 잘못된 트랜잭션 설정을 감지하자" 제안 | issue #30679 | [검색 요약] |
| #31414 | 검증을 더 엄격하게 — `@TransactionalEventListener` 메서드의 `@Transactional`은 `REQUIRES_NEW`로 선언될 때만 허용 | issue #31414 | [검색 요약] |
| #31907 / PR #33470 | `NOT_SUPPORTED`도 허용 추가 (현재 트랜잭션을 suspend하므로 의미상 안전) | issue #31907, PR #33470 | [검색 요약] |
| 6.1 | transactional event listener가 thread-bound + reactive 트랜잭션과 동작하도록 정리 (이 fail-fast 검증과는 별개) | `TransactionalEventListener` Javadoc "As of 6.1" | [출처 확인] |
| 6.2 (M1~) | `RestrictedTransactionalEventListenerFactory` 검증 도입, `@EnableTransactionManagement` 활성 시 교체 등록 | 이슈 #32319 (6.2.0-M1 맥락) | [검색 요약] |

> 즉 "6.1+에서 예외가 난다"는 표현은 부정확합니다. **검증은 6.2부터**입니다. 본 프로젝트는 Boot 3.4.4(Framework 6.2.x)라 검증 대상에 포함됩니다.

## 5. 올바른 사용법

```kotlin
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onLiked(event: ProductLikedEvent) { ... }
```

- `phase = AFTER_COMMIT`: 원본 트랜잭션이 성공적으로 커밋된 뒤에만 실행 (롤백되면 미실행).
- `propagation = REQUIRES_NEW`: 리스너 안의 DB 쓰기를 **독립 트랜잭션**으로 커밋. `REQUIRED`였다면 컨텍스트 초기화 시점에 `IllegalStateException`으로 기동 실패.
- DB 쓰기가 없고 트랜잭션이 불필요하면 `NOT_SUPPORTED`도 가능.

`@Async`와의 관계: `@Async`는 리스너를 별도 스레드로 옮깁니다. 새 스레드에는 원본 thread-bound 트랜잭션 컨텍스트가 없으므로, 거기서 `@Transactional(REQUIRES_NEW)`는 깨끗한 새 트랜잭션을 엽니다. `@Async`가 없으면 동일 스레드에서 `afterCompletion` 콜백 중에 실행되어, suspend 후 새 트랜잭션을 여는 동작에 의존하게 됩니다(여전히 `REQUIRES_NEW`/`NOT_SUPPORTED` 필요). 본 프로젝트는 호출자 응답 지연을 피하려고 `@Async`를 추가했습니다.

## 6. 본 프로젝트 적용

해당 핸들러:

- `apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeEventHandler.kt`
- `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductLikeCountEventHandler.kt`

두 핸들러 모두 `onLiked`/`onUnliked`에 `@Async` + `@Transactional(propagation = Propagation.REQUIRES_NEW)` + `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`를 동일하게 적용하고 있습니다. **현재 설정은 위 검증을 통과하는 올바른 형태입니다.**

조합 이유:

- **AFTER_COMMIT**: 좋아요 토글(본 트랜잭션)이 실제 커밋된 뒤에만 이력 적재/카운트 갱신을 수행. 본 트랜잭션이 롤백되면 부수효과도 발생하지 않아야 함.
- **REQUIRES_NEW**: AFTER_COMMIT 시점에 원본 트랜잭션은 이미 완료됐으므로 `REQUIRED`로 참여하면 쓰기가 커밋되지 않음(§2). 부수효과(`LikeEvent` append, `like_count` 증감)는 독립 트랜잭션으로 커밋되어야 함. 동시에 부수효과 실패가 본 토글을 롤백시키지 않도록 트랜잭션 경계를 분리하는 의미도 있음 — 두 핸들러 모두 내부 `try-catch`로 실패를 로깅만 하고 삼키므로(결과적 일관성, 재집계로 복구) 경계 분리가 설계 의도와 맞음.
- **@Async**: 좋아요 토글 응답을 부수효과 처리 시간만큼 지연시키지 않기 위함. 새 스레드 + 새 트랜잭션이라 본 요청 트랜잭션과 완전히 독립.

주의: `@Async`로 다른 스레드에서 도는 부수효과는 본 트랜잭션과 별개라, 통합 테스트에서 테스트 클래스에 `@Transactional`을 일괄로 붙이면 propagation 경계가 어긋나 롤백/가시성 문제가 생깁니다 (CLAUDE.md §6의 `DatabaseCleanup` + `@BeforeEach` 정책이 이 함정을 피하는 이유와 동일 맥락).

## 7. 출처 표

| # | 출처 | URL | 확인 방식 | 신뢰도 |
|---|---|---|---|---|
| 1 | Spring Framework Reference — Transaction-bound events | https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html | WebFetch 성공 | 출처 확인 |
| 2 | Javadoc — `TransactionalEventListener` (AFTER_COMMIT WARNING, "As of 6.1") | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html | WebFetch 성공 | 출처 확인 |
| 3 | Javadoc — `TransactionalEventListenerFactory` (since 4.2, Restricted의 상위 클래스) | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListenerFactory.html | WebFetch 성공 | 출처 확인 |
| 4 | Javadoc — `Propagation` (REQUIRES_NEW / NOT_SUPPORTED 원문) | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html | WebFetch 성공 | 출처 확인 |
| 5 | Javadoc — `RestrictedTransactionalEventListenerFactory` | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/RestrictedTransactionalEventListenerFactory.html | WebFetch 404 (공개 페이지 없음 — internal 클래스 추정) | 차단/부재 |
| 6 | GitHub source — `RestrictedTransactionalEventListenerFactory.java` (예외 메시지 원문) | https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/event/RestrictedTransactionalEventListenerFactory.java | WebFetch 차단 (github.com 권한 거부) + raw 경로 404 | 차단 |
| 7 | Issue #30679 "Detect invalid transaction configuration" | https://github.com/spring-projects/spring-framework/issues/30679 | WebFetch 차단 → 검색 요약 | 검색 요약 |
| 8 | Issue #31414 "Enforce REQUIRES_NEW" | https://github.com/spring-projects/spring-framework/issues/31414 | WebFetch 차단 → 검색 요약 | 검색 요약 |
| 9 | Issue #31907 "Allow Propagation.NOT_SUPPORTED" | https://github.com/spring-projects/spring-framework/issues/31907 | WebFetch 차단 → 검색 요약 | 검색 요약 |
| 10 | PR #33470 "Throw runtime error ... not REQUIRES_NEW or NOT_SUPPORTED" | https://github.com/spring-projects/spring-framework/pull/33470 | WebFetch 차단 → 검색 요약 | 검색 요약 |
| 11 | Issue #32319 "invoked like standard listener if @EnableTransactionManagement not active" (6.2.0-M1 맥락, 팩토리 교체 등록) | https://github.com/spring-projects/spring-framework/issues/32319 | WebFetch 차단 → 검색 요약 | 검색 요약 |
| 12 | 프로젝트 버전 (`gradle.properties`: springBootVersion=3.4.4 → Framework 6.2.x) | (로컬) | Read/grep 직접 확인 | 출처 확인 |

### 차단/미확인 항목 정리

- `RestrictedTransactionalEventListenerFactory`의 **예외 메시지 글자 단위 원문**: github.com(이슈/소스) WebFetch 권한 거부 + raw.githubusercontent.com 경로 404로 직접 확인 불가. 의미(REQUIRES_NEW/NOT_SUPPORTED만 허용, 그 외 `IllegalStateException`)는 다수 1차/2차 출처에서 일관되게 확인됨.
- **6.2.0 정확한 마일스톤 번호**: 이슈 #32319가 6.2.0-M1 맥락으로 나타났으나, GA 버전에서의 정확한 도입 지점은 검색 요약 기반.

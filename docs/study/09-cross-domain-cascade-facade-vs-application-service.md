# Cross-Domain Cascade: Facade vs Application Service vs Domain Service

> 주제: DDD layered architecture(`interfaces → application → domain ← infrastructure`) + Spring 환경에서
> "cross-domain 오케스트레이션(예: 브랜드 삭제 시 그 브랜드의 Product들 cascade soft delete)을
> Application Service / Facade / Domain Service 중 어디에 둘지"를 정리한 기술 레퍼런스입니다.
>
> **출처 신뢰도에 대한 정직한 고지**: 이 주제는 *공식 스펙(spec)으로 강제되는 영역이 아니라 DDD 설계 관습(convention) 영역*이 큽니다.
> Spring/JPA가 강제하는 부분(이벤트 발행 API, 트랜잭션 경계 동작)은 공식 docs로 검증되지만,
> "어느 service에 둘지"는 Evans/Vernon의 *권고*와 *팀 규칙*이 섞여 있습니다.
> 이 문서는 **[출처 확인]**(직접 fetch 검증), **[관습]**(DDD 커뮤니티/저자 권고이나 강제 아님),
> **[이 프로젝트 규칙]**(이 저장소 `.claude/skills/ddd-layered-tdd/SKILL.md`에서 합의된 로컬 규칙)을 명시적으로 구분합니다.

---

## ① 한 줄 결론 + 의사결정 표

**한 줄 결론**: "브랜드 삭제 + 그 브랜드 Product 일괄 soft delete"는 *독립 use case 2개를 한 요청에 엮는 것*이므로
**Facade**(`BrandService.delete` + `ProductService.softDeleteByBrand` 조합)에 둡니다. cascade를 비동기로 풀고 싶거나
Product 쪽 후속작업이 늘어나면 도메인 이벤트(`BrandDeleted` → Product 리스너)가 대안입니다.

### 결정 표 — 조합 단위별 배치

| 조합 단위(무엇을 엮는가) | 배치 위치 | 근거 출처 |
|---|---|---|
| **단순 read 가드** (다른 도메인 존재 여부 확인 등, write 없음) | 포트 인라인 — 자기 app service가 다른 도메인 `Repository` 포트로 `existsById`/조회 | [이 프로젝트 규칙] SKILL §3 |
| **규칙에 묶인 cross-aggregate 상태변경** (떼면 도메인 의미가 어색해지는 write) | **Domain Service** (domain 레이어) — 다른 도메인 Entity + Repository 포트 + 도메인 메서드만 조합 | [관습] Evans 3조건 + [이 프로젝트 규칙] SKILL §0.4, §2 |
| **독립 use case 2개+** (각자 자기 app service를 소유한, 단독으로 떼어낼 수 있는 작업들) | **Facade** (application 레이어) — app service들을 조합 | [이 프로젝트 규칙] SKILL §0.3, §2 표 / [관습] Service Layer 조정 책임 |
| **비동기 decoupling** (cascade를 호출자 트랜잭션에서 떼어 eventual consistency로) | **도메인 이벤트** (`@DomainEvents` 또는 `ApplicationEventPublisher` → 리스너) | [출처 확인] Spring docs |

> 판별 한 줄(SKILL §3 인용): **"이 작업을 그 도메인 app service의 단독 use case로 떼어낼 수 있나?"**
> 떼어낼 수 있는 게 2개+ → Facade. 떼면 어색한 규칙-묶인 상태변경 → Domain Service. 단순 read → 인라인(포트).

---

## ② 세 레이어 책임 비교

| 구분 | Domain Service | Application Service | Facade |
|---|---|---|---|
| 소속 레이어 | domain | application | application |
| 조합 단위 | 도메인 **객체**(Entity/VO/port) → 하나의 도메인 연산 | 자기 도메인 use case **1개** | use case(app service) **여러 개** |
| 반환 | 도메인 객체 | `{D}Info`/DTO (Entity 그대로 노출 안 함) | `{D}Info`/DTO |
| 트랜잭션 경계 | **소유하지 않음** (stateless 도메인 연산) | `@Transactional` 소유 | 오케스트레이션 트랜잭션 경계 |
| 다른 도메인 접근 | 다른 도메인 **Repository 포트 + 도메인 메서드만** (app service 호출 ❌) | cross-domain **READ는 포트로 인라인 OK**, 다른 app service 호출 ❌ | 여러 app service 호출 가능 (**유일하게 허용된 자리**) |

### 어디까지가 공식 정의이고 어디부터가 관습인가

- **Application Service / Service Layer가 "트랜잭션을 제어하고 응답을 조정한다(coordinates), 비즈니스 규칙은 담지 않는다"** — Fowler "Service Layer"(PoEAA, Randy Stafford):
  > "encapsulates the application's business logic, controlling transactions and coordinating responses in the implementation of its operations." [출처 확인]
- **Service Layer는 thin해야 하고 비즈니스 규칙은 domain object에 둔다** — Fowler가 Evans DDD를 인용:
  > "This layer is kept thin. It does not contain business rules or knowledge, but only coordinates tasks and delegates work to collaborations of domain objects" (Evans, *Domain-Driven Design*, Fowler "AnemicDomainModel"에서 인용). [출처 확인]
  > "The logic that should be in a domain object is domain logic - validations, calculations, business rules" / "If all your logic is in services, you've robbed yourself blind." [출처 확인]
- **Domain Service의 "Evans 3조건"** (어느 Entity에도 자연스럽게 안 붙음 / 도메인 객체로 표현됨 / stateless) — Evans *Domain-Driven Design*의 Service 챕터 권고로 알려진 기준. 원문 직접 fetch는 못 했으므로 *(출처 미확인 — 2차 인용)*. 이 프로젝트는 이 3조건을 SKILL §2 결정 트리에 채택했습니다. [관습 + 이 프로젝트 규칙]
- **"Facade"라는 이름과 "app service는 app service를 호출하지 않는다"는 규칙**: GoF Facade 패턴은 "서브시스템 묶음에 통합 인터페이스 제공"이라는 일반 패턴이고, **DDD 표준 building block은 아닙니다**(Evans의 공식 building block은 Entity/Value Object/Service/Aggregate/Repository/Factory). "여러 use case 조합은 Facade만, app service끼리 직접 호출 금지"는 *이 저장소가 채택한 로컬 규칙*입니다(아래 §3 근거). [관습 + 이 프로젝트 규칙]

요약: **"트랜잭션은 application/service 레이어가 소유", "도메인 규칙은 domain object/Domain Service에"** 까지는 Fowler/Evans 정의입니다.
**"조합 전용 Facade를 따로 두고 app service 간 직접 호출을 금지"** 는 이 프로젝트(및 다수 팀)의 *관습*입니다.

---

## ③ 왜 cascade는 Facade인가 — 우리 케이스 적용

대상 작업: **"브랜드 삭제 시 그 브랜드의 Product들을 cascade soft delete"**.

SKILL §3의 판별 기준을 적용합니다.

1. **단일 Entity의 invariant인가?** 아니오 — Brand와 Product 두 Aggregate에 걸칩니다.
2. **떼면 어색한 "도메인 규칙에 묶인" 상태변경인가? (Domain Service 후보)** — 검토 결과 아니오.
   - "재고 차감"은 "주문 체결"이라는 단일 도메인 규칙의 일부라 떼면 어색합니다(Domain Service 적합).
   - 그러나 **"Product 일괄 soft delete"는 그 자체로 Product 도메인의 완결된 독립 use case**입니다.
     관리자가 "브랜드 단종 처리" 외의 경로(예: 카테고리 정리, 운영 배치)로도 호출할 수 있고, Product 쪽 정책(연관 like/재고 처리 등)을 독자적으로 가질 수 있습니다.
   - 즉 Brand 삭제 규칙에 *본질적으로 묶인* 게 아니라, *오케스트레이션으로 엮이는* 두 use case입니다.
3. **독립 use case 2개+를 한 요청에 엮는가? (Facade)** — 예.
   - `BrandService.delete(brandId)` : Brand 도메인의 단독 use case.
   - `ProductService.softDeleteByBrand(brandId)` : Product 도메인의 단독 use case.
   - 둘 다 각자 자기 app service에서 독립적으로 의미를 가지며, "브랜드 삭제"라는 한 요청이 둘을 순차 조합합니다.
   - → **Facade**가 정답. app service가 다른 app service를 직접 부르면 안 되므로(§아래) `BrandService`가 `ProductService`를 부르는 것도 금지 → 조합은 `BrandFacade`로.

```kotlin
// 개념 예시 (Product app service 머지 후 채울 형태)
@Component
class BrandFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    @Transactional
    fun delete(brandId: Long) {
        brandService.delete(brandId)               // 독립 use case 1
        productService.softDeleteByBrand(brandId)  // 독립 use case 2
    }
}
```

> 트랜잭션 경계 주의: 두 use case를 한 원자 단위로 묶으려면 Facade 메서드에 `@Transactional`을 두어
> 두 app service 호출이 같은 물리 트랜잭션에 참여하게 합니다(Spring 기본 propagation `REQUIRED`).
> app service 각각의 `@Transactional`은 이미 열린 트랜잭션에 합류합니다. [출처 확인 — Spring 기본 propagation 동작]

---

## ④ Facade 동기 호출 vs 도메인 이벤트 — 트레이드오프

같은 cascade를 (A) Facade에서 동기 호출하거나 (B) 도메인 이벤트(`BrandDeleted` 발행 → Product 리스너)로 풀 수 있습니다.

| 축 | (A) Facade 동기 호출 | (B) 도메인 이벤트 |
|---|---|---|
| 결합도 | Brand 흐름이 Product app service를 **컴파일 타임에 알아야** 함(application → application 조합) | Brand는 이벤트만 발행, Product를 **모름**(decoupling) |
| 일관성 | 한 트랜잭션 → **강한 일관성**(둘 다 commit 또는 둘 다 rollback) | 리스너 phase에 따라 다름. `@TransactionalEventListener(AFTER_COMMIT)`면 Brand commit 후 별도 처리 → **eventual consistency** |
| 실패 처리 | Product 삭제 실패 시 Brand 삭제도 함께 rollback(원자성) | AFTER_COMMIT 리스너는 이미 Brand가 commit된 뒤 — 실패해도 Brand는 안 돌아감. 보상/재시도 필요 |
| 추적/디버깅 | 호출 스택이 직선 → 추적 쉬움 | 발행-구독 간접 → 흐름 추적 어려움 |
| 트랜잭션 미묘함 | 명시적·단순 | `@TransactionalEventListener`의 `AFTER_COMMIT`/`AFTER_ROLLBACK`/`AFTER_COMPLETION`에서 실행되는 data access는 "원래 트랜잭션에 참여하지만 **변경이 커밋되지 않는다**" — 새 트랜잭션을 명시하지 않으면 쓰기가 유실됨 [출처 확인] |
| 확장성 | 후속작업(N개)이 늘면 Facade가 점점 비대 | 리스너 추가만으로 확장(Brand 코드 불변) — 후속작업이 여러 개·여러 도메인일 때 유리 |
| 비동기 | 불가(동기) | `@Async` + `@EventListener`로 비동기 가능. 단 **예외가 호출자로 전파되지 않고, 반환값으로 후속 이벤트 발행 불가, ThreadLocal/로깅 컨텍스트 기본 미전파** [출처 확인] |

### Spring에서 도메인 이벤트를 발행하는 두 경로 [출처 확인]

1. **Aggregate root 기반** — Spring Data `@DomainEvents` / `@AfterDomainEventPublication`:
   - `@DomainEvents` 메서드(인자 없음, 단일 또는 컬렉션 반환)가 발행할 이벤트를 돌려주고,
     repository의 `save(...)`, `saveAll(...)`, `delete(...)`, `deleteAll(...)` 등 호출 시 자동 발행.
   - **`deleteById(...)`는 발행 대상이 아님** — aggregate 인스턴스 없이 query 기반 삭제일 수 있어서. [출처 확인]
   - `@AfterDomainEventPublication` 콜백으로 이벤트 리스트 정리.
2. **명시 발행** — `ApplicationEventPublisher.publishEvent(...)`:
   - service에서 직접 발행. 리스너는 `@EventListener` / `@TransactionalEventListener`. [출처 확인]
   - 기본 동작: **listener는 동기**(`publishEvent()`는 모든 리스너가 끝날 때까지 블록). [출처 확인]

> 우리 cascade가 soft delete라 `deleteById`가 아니라 `save`(deletedAt 갱신)/벌크 업데이트 경로일 가능성이 큽니다.
> `@DomainEvents`는 aggregate 인스턴스를 거치는 save/delete에서만 발행되므로, 벌크 업데이트로 soft delete 하면
> `@DomainEvents`가 트리거되지 않습니다 — 이 경우 `ApplicationEventPublisher` 명시 발행이 적합. *(우리 구현 형태 확정 전이므로 적용은 Product 머지 후 재판단)*

**언제 이벤트로 갈 것인가(권고)**: 후속작업이 (a) 여러 개로 늘거나, (b) 여러 도메인에 퍼지거나, (c) 비동기/eventual로 풀어도 되거나,
(d) Brand가 Product를 *모르게* 하는 decoupling 가치가 결합도 비용보다 클 때. 지금은 후속작업이 1개(Product soft delete)뿐이고
강한 일관성이 자연스러우므로 **YAGNI 원칙상 동기 Facade가 우선**, 이벤트는 확장 시점의 대안으로 기록합니다. [관습 + 이 프로젝트 규칙 YAGNI]

---

## ⑤ "app service가 app service를 직접 호출하면 안 되는" 이유 + soft reference 패턴

### 왜 app service 간 직접 호출을 금지하는가 [이 프로젝트 규칙, 일반 관습으로도 흔함]

이 저장소 SKILL §0.3은 "application service는 다른 application service를 호출하지 않는다. 여러 use case 조합은 Facade만"을 절대 규칙으로 둡니다. 근거:

1. **트랜잭션 경계 혼란**: app service A가 B를 부르면 누가 트랜잭션 경계 주인인지 흐려집니다.
   각 app service가 자기 `@Transactional`을 갖는데 서로 부르면 propagation(누가 `REQUIRED`로 합류하고 누가 새로 여는지)이 암묵적으로 얽혀, "어디까지가 한 원자 단위인가"가 호출 그래프에 따라 달라집니다. 조합 트랜잭션 경계는 **Facade 한 곳**에 명시하는 편이 추론 가능합니다.
2. **순환 의존**: A→B 직접 호출을 허용하면 곧 B→A가 생겨 application 레이어 안에 순환 의존이 만들어집니다. 조합을 Facade로 끌어올리면 app service들은 서로를 모르고(단방향), Facade만 다수를 압니다.
3. **재사용/단일 책임**: app service는 "자기 도메인의 단일 use case"라는 책임만 유지해야 다른 컨텍스트(다른 Facade, 다른 진입점)에서 그대로 재사용됩니다. 다른 app service 호출 책임이 끼면 그 use case가 특정 조합에 종속됩니다.
4. **테스트 용이성**: app service 단위 테스트는 Repository 포트·협력자 mock으로 자기 use case만 검증하면 됩니다. 다른 app service를 부르면 그 service의 의존 전체를 mock해야 해 테스트가 무거워지고 경계가 흐려집니다. 조합 검증은 Facade 테스트로 분리됩니다.

> 정직한 구분: 1~4의 *문제 자체*(트랜잭션 경계·순환·결합)는 일반적으로 인정되는 설계 우려입니다. 다만 **"그래서 반드시 Facade라는 별도 클래스를 둔다"는 해법은 강제 표준이 아니라 이 프로젝트가 택한 관습**입니다. (어떤 팀은 app service가 다른 app service를 부르는 것을 허용하기도 합니다.)

### soft reference(도메인 간 FK 없음) + application cascade 패턴 [이 프로젝트 규칙, 관습으로도 표준적]

SKILL §3 인용: **"도메인 간 DB FK 없음 — soft reference(id). Aggregate 안 = 객체참조 + JPA cascade, 사이 = id 참조. cascade는 application에서 명시 호출(그게 여러 app service write를 엮으면 Facade 후보)."**

- **Aggregate 경계 규칙**(Vernon "Implementing DDD"의 권고로 알려짐, 원문 직접 fetch 못함 — *(출처 미확인 — 2차 인용)*): 다른 Aggregate는 **직접 객체 참조가 아니라 id로 참조**한다. 한 트랜잭션에서 한 Aggregate만 수정하는 것을 기본으로 한다.
- 따라서 Brand와 Product는 서로 **DB FK로 묶지 않고** Product가 `brandId`(soft reference)만 가집니다. DB `ON DELETE CASCADE`에 의존하지 않습니다.
- cascade(브랜드 삭제 → Product soft delete)는 **DB나 JPA cascade가 아니라 application 레이어가 명시적으로** 수행합니다. 그 cascade가 *여러 app service의 write를 엮으면* → Facade(우리 케이스). *단일 도메인 규칙에 묶인 cross-aggregate write*면 → Domain Service.
- JPA `cascade = CascadeType.*`는 **한 Aggregate 내부**(Aggregate root → 그 구성요소)에만 쓰고, **Aggregate 사이**에는 쓰지 않습니다. Brand→Product는 별개 Aggregate이므로 JPA cascade 대상이 아닙니다.

---

## ⑥ 출처 / 참조 목록

### 직접 fetch로 검증한 1차 출처 [출처 확인]

- Martin Fowler, *Service Layer* (PoEAA, Randy Stafford) — Service Layer가 트랜잭션 제어 + 응답 조정 책임.
  `https://martinfowler.com/eaaCatalog/serviceLayer.html`
- Martin Fowler, *AnemicDomainModel* — 도메인 로직은 domain object에, service layer는 thin(Evans 인용 포함).
  `https://martinfowler.com/bliki/AnemicDomainModel.html`
- Spring Data, *Publishing Events from Aggregate Roots* — `@DomainEvents` / `@AfterDomainEventPublication`, `save`/`delete`에서 발행, `deleteById` 제외.
  `https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/core-domain-events.html`
- Spring Framework Reference, *Standard and Custom Events* — `ApplicationEventPublisher.publishEvent`, `@EventListener`, `@Async` 리스너 한계(예외 미전파 등), 기본 동기 발행.
  `https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html`
- Spring Framework Javadoc, *`@TransactionalEventListener`* — `TransactionPhase`(BEFORE_COMMIT/AFTER_COMMIT(default)/AFTER_ROLLBACK/AFTER_COMPLETION), AFTER_* phase의 data access가 "참여하지만 커밋되지 않음" 경고.
  `https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html`

### 이 저장소 로컬 규칙 [이 프로젝트 규칙]

- `.claude/skills/ddd-layered-tdd/SKILL.md`
  - §0.3 "application service는 다른 application service를 호출하지 않는다 — 조합은 Facade만"
  - §0.4 "domain service는 다른 도메인의 application service를 호출하지 않는다 — 다른 도메인 Entity + Repository 포트 + 도메인 메서드만"
  - §2 "어디에 둘까 결정 트리" + 세 Service 구분 표
  - §3 "cross-domain 규칙" — 판별 기준은 "건드리는 도메인 개수/write 개수"가 아니라 "조합 단위가 독립 use case인가", soft reference + application cascade.

### 직접 확인 못 한 2차 인용 (출처 미확인)

- Eric Evans, *Domain-Driven Design* — Domain Service의 3조건(어느 Entity에도 안 붙음 / 도메인 객체로 표현 / stateless),
  "Service layer is kept thin" 원문. 원서/공식 페이지를 직접 fetch하지 못함. Fowler의 *AnemicDomainModel*이 thin service 문장은 Evans를 인용해 [출처 확인]됨. **(원서 직접 출처 미확인)**
- Vaughn Vernon, *Implementing Domain-Driven Design* — Aggregate는 id로 서로 참조, "한 트랜잭션 한 Aggregate" 권고, application service vs domain service 구분. 원서/공식 페이지 직접 fetch하지 못함. **(출처 미확인 — 2차 인용)**
- GoF Facade 패턴이 DDD 공식 building block이 아니라는 점은 Evans의 building block 목록(Entity/Value Object/Service/Aggregate/Repository/Factory)에 Facade가 없다는 사실에 근거 — 목록 자체는 널리 알려졌으나 본 작성 시 원서 직접 fetch는 안 함. **(2차 인용)**

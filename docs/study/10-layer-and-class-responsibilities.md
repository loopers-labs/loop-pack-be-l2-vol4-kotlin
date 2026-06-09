# 레이어와 클래스 책임 정의 (공통 기준)

> 대상 환경: Kotlin / Spring Boot / Java 21, `commerce-api`.
> 아키텍처: layered architecture(`interfaces → application → domain ← infrastructure`) + 의존성 역전(DIP)으로
> domain이 다른 레이어에 의존하지 않도록 유지.
>
> 이 문서는 **도메인별 예시(Brand/Product/Like 등)가 아니라 공통 기준/정의**만 다룹니다.
> 각 정의의 근거는 공식 문서·표준 레퍼런스로 검증했고, 문서 끝 "출처" 표에 URL과 확인 방식을 적었습니다.
>
> **표기 규칙 — 근거 신뢰도 구분**
> - **[출처 확인]**: 원문을 직접 fetch해 인용을 확인한 결론.
> - **[차단]**: fetch가 차단되어 원문 인용을 직접 확인하지 못한 항목(없는 인용을 지어내지 않음).
> - **[이 프로젝트 결정]**: 공식/빅테크 문서가 직접 뒷받침하지 않는, 이 저장소가 추론으로 채택한 로컬 규칙.
>
> 각 사실 주장 뒤의 `[n]`은 문서 말미 출처 목록 번호입니다.

---

## 0. 한 줄 결론

- **의존 방향은 항상 안쪽(domain)을 향한다.** domain은 application/infrastructure를 import하지 않고,
  infrastructure가 domain에 정의된 Repository port(인터페이스)를 구현한다(DIP) `[1][7]`. **[출처 확인]**
- **비즈니스 규칙은 domain에, 오케스트레이션·트랜잭션은 application에, HTTP 변환은 interfaces에, 기술 연동은 infrastructure에** 둔다 `[1]`. **[출처 확인]**
- **"이 로직 어디 둘까"** 는 §13 결정 트리로 판별한다.

---

## 1. 의존 방향 / 레이어 정의

### 1.1 의존 방향

```
interfaces  ─▶  application  ─▶  domain  ◀─  infrastructure
(presentation)   (use case)      (규칙)       (adapter)
```

- application은 domain과 infrastructure에 의존할 수 있고, infrastructure는 domain에 의존하지만,
  **domain은 어떤 레이어에도 의존하지 않는다** `[1]`. Microsoft DDD 가이드 원문:
  > "the Application layer depends on Domain and Infrastructure, and Infrastructure depends on Domain, but Domain doesn't depend on any layer." `[1]` **[출처 확인]**
  > "the domain model layer should not take a dependency on any other layer" `[1]` **[출처 확인]**
- domain이 영속성·인프라 기술을 직접 참조하지 않도록 하는 것이 핵심 규칙이다 `[1]`:
  > "this layer must completely ignore data persistence details. These persistence tasks should be performed by the infrastructure layer." `[1]` **[출처 확인]**
- 이 "안쪽으로만 향하는 의존" 원칙은 Clean Architecture의 The Dependency Rule(소스 의존성은 안쪽으로만, inner는 outer를 알지 못함)과 같은 결론이다.
  단, Clean Architecture 원문(`blog.cleancoder.com`)은 이 환경에서 **fetch가 차단**되어 직접 인용은 확인하지 못했다 `[8]`. **[차단]**
  Microsoft DDD 가이드 `[1]`가 동일한 의존 방향을 명시하므로 결론 자체는 검증된 근거로 뒷받침된다. **[출처 확인]**

### 1.2 각 레이어 책임 + "여기 두면 안 되는 것"

| 레이어 | 책임 (둘 것) | 여기 두면 안 되는 것 |
|---|---|---|
| **interfaces (presentation)** | Controller, 웹 요청/응답 DTO, HTTP 매핑/상태 코드, 입력 바인딩 | 도메인 Entity 직접 노출(반환), 비즈니스 규칙, 트랜잭션 경계 |
| **application** | use case 오케스트레이션, 트랜잭션 경계, 인증·인가·입력 검증, DTO 매핑(Info 생성) | 비즈니스 규칙 자체(도메인에 위임), 영속성 디테일, HTTP 관심사 |
| **domain** | Entity, Value Object, Domain Service, Repository port(인터페이스), ErrorCode, 도메인 불변식/규칙 | 프레임워크 의존(영속성/웹), use case 오케스트레이션, 트랜잭션 관리 |
| **infrastructure** | Repository 구현체(adapter), JPA/외부 시스템 연동, 이벤트 적재 어댑터 | 비즈니스 규칙, use case 오케스트레이션 |

- **interfaces — Entity 직접 노출 금지** `[1]`:
  > "the domain entity is contained within the domain model layer and should not be propagated to other areas that it does not belong to, like to the presentation layer." `[1]` **[출처 확인]**
  > "entities should not be bound to client views ... The ViewModel is a data model exclusively for presentation layer needs." `[1]` **[출처 확인]**
- **application — thin, 규칙은 domain에 위임** `[1]`. Microsoft가 인용한 Evans의 application layer 정의:
  > "This layer is kept thin. It does not contain business rules or knowledge, but only coordinates tasks and delegates work to collaborations of domain objects in the next layer down." `[1]` **[출처 확인]**
  > "The application layer must only coordinate tasks and must not hold or define any domain state ... It delegates the execution of business rules to the domain model classes themselves." `[1]` **[출처 확인]**
- **domain — business의 심장, 영속성은 인프라에 위임** `[1]`. Microsoft가 인용한 Evans의 domain layer 정의:
  > "Responsible for representing concepts of the business, information about the business situation, and business rules ... even though the technical details of storing it are delegated to the infrastructure. This layer is the heart of business software." `[1]` **[출처 확인]**
- **infrastructure — domain을 오염시키지 않는다** `[1]`:
  > "the infrastructure layer must not 'contaminate' the domain model layer." `[1]` **[출처 확인]**

---

## 2. Entity

| 항목 | 내용 |
|---|---|
| **정의** | 식별자(identity)로 동일성이 결정되는 도메인 객체. 같은 ID면 속성이 달라도 같은 엔티티 `[2]`. |
| **책임** | 자신의 데이터 + 그 데이터에 대한 행위(불변식·규칙·상태 전이)를 메서드로 보유. |
| **두는 위치** | domain |
| **판별 기준** | "이 객체를 *who*(누구인지)로 구분하는가?" → 그렇다면 Entity. 시간에 걸친 연속성/식별이 필요하면 Entity. |

근거 `[2]`:
> "Entities represent domain objects and are primarily defined by their identity, continuity, and persistence over time, and not only by the attributes that comprise them. As Eric Evans says, 'an object primarily defined by its identity is called an Entity.'" `[2]` **[출처 확인]**

**Entity는 데이터뿐 아니라 행위를 가져야 한다(anemic 회피)** `[2]`:
> "A domain entity in DDD must implement the domain logic or behavior related to the entity data ... The entity's methods take care of the invariants and rules of the entity instead of having those rules spread across the application layer." `[2]` **[출처 확인]**

Anemic Domain Model은 행위 없는 getter/setter 덩어리이며, 도메인 로직(검증·계산·규칙)은 도메인 객체에 있어야 한다 `[6]`:
> "there is hardly any behavior on these objects, making them little more than bags of getters and setters." `[6]` **[출처 확인]**
> "The logic that should be in a domain object is domain logic - validations, calculations, business rules." `[6]` **[출처 확인]**

> 참고(원전): Entity 개념은 Eric Evans, *Domain-Driven Design* 의 building block. 책 원문 직접 fetch는 하지 않았고 `[2][6]`가 2차 인용으로 확인. **[차단 — 원전 미열람]**

### 2.1 Aggregate / Aggregate Root (Entity와 함께 다루는 경계 개념)

- Aggregate는 하나의 단위로 다뤄지는 도메인 객체 묶음이고, 외부 참조는 root로만, 트랜잭션은 경계를 넘지 않는다 `[5]`:
  > "A DDD aggregate is a cluster of domain objects that can be treated as a single unit." `[5]` **[출처 확인]**
  > "Any references from outside the aggregate should only go to the aggregate root." `[5]` **[출처 확인]**
  > "Transactions should not cross aggregate boundaries." `[5]` **[출처 확인]**
- 이 규칙이 §7(Repository는 aggregate root당 하나)·§8(읽기는 경계 무시)·§11(이벤트로 cross-aggregate 부수효과)의 근거다.

---

## 3. Value Object

| 항목 | 내용 |
|---|---|
| **정의** | 고유 식별자가 없고, 보유한 값(속성)으로 동일성이 결정되는 객체 `[2][3]`. |
| **책임** | 값 표현 + 생성 시 불변식 검증. 불변(immutable). |
| **두는 위치** | domain |
| **판별 기준** | "이 객체를 *what*(무엇인지)으로만 구분하는가? 식별자가 필요 없나? 값이 같으면 같은 것으로 취급해야 하나?" → 그렇다면 VO. **가변 카운터(예: 누적 수치)는 VO가 아니다** — 시간에 따라 변해야 하므로 불변 요건과 충돌. |

근거 `[3]`:
> "Objects that are equal due to the value of their properties ... are called value objects." `[3]` **[출처 확인]**
> "value objects should be immutable" `[3]` **[출처 확인]**

VO의 두 핵심 특성 `[4]`:
> "There are two main characteristics for value objects: They have no identity. They are immutable." `[4]` **[출처 확인]**
> "The values of a value object must be immutable once the object is created. Therefore, when the object is constructed, you must provide the required values, but you must not allow them to change during the object's lifetime." `[4]` **[출처 확인]**

값 동등성(value equality)은 모든 속성 비교로 구현한다 `[4]`:
> "equality based on the comparison between all the attributes (since a value object must not be based on identity)" `[4]` **[출처 확인]**

**생성 시 검증(fail-fast)** — VO 생성자/`init`에서 불변식을 강제해, 잘못된 값이 도메인 내부로 들어오지 못하게 막는다.
이는 "경계에서 검증, 내부에서는 신뢰" 원칙의 도메인 표현이다. **[이 프로젝트 결정 — VO `init` 검증 컨벤션]**

> 가변 카운터를 VO로 잘못 모델링하면 불변 요건(`[3][4]`)과 충돌한다. 누적·증감하는 수치는 그 값을 소유한 Entity의 상태(필드 + 상태전이 메서드)로 둔다. **[이 프로젝트 결정 — `[4]` 불변 요건의 자연 귀결]**

---

## 4. Domain Service

| 항목 | 내용 |
|---|---|
| **정의** | 특정 Entity/VO에 자연스럽게 속하지 않는 도메인 연산을 담는, stateless한 도메인 객체. |
| **책임** | 여러 aggregate에 걸친 도메인 규칙(상태 없는 연산). |
| **두는 위치** | domain |
| **다른 도메인 접근** | 다른 도메인의 **Repository port + 도메인 메서드만** 사용. application service를 호출하지 않음. |
| **판별 기준** | (1) 어떤 Entity/VO에도 자연스럽게 안 붙고, (2) 도메인 개념으로 표현되며, (3) stateless인가? |

- domain은 비즈니스 규칙을 담는 레이어이므로 `[1]`, Entity에 안 붙는 도메인 규칙은 application이 아니라 domain의 service로 둔다.
- "Domain Service의 3조건(어느 Entity에도 자연스럽게 안 붙음 / 도메인 객체로 표현됨 / stateless)"은 Evans의 Service 챕터 권고로 알려진 기준이다. 책 원문 직접 fetch는 하지 않았다. **[차단 — 원전 미열람] / [이 프로젝트 결정 — 결정 트리에 채택]**
- Domain Service는 트랜잭션 경계를 **소유하지 않는다**(트랜잭션은 application이 소유, §12). **[이 프로젝트 결정]**

---

## 5. Application Service

| 항목 | 내용 |
|---|---|
| **정의** | 단일 use case를 오케스트레이션하고 트랜잭션 경계를 소유하는 application 레이어 서비스. |
| **책임** | use case 흐름 조정, 트랜잭션 경계(`@Transactional`), 인증/입력 검증, 결과 DTO(Info) 반환. |
| **두는 위치** | application |
| **다른 도메인 접근** | cross-domain **READ는 다른 도메인 Repository port로 인라인** 가능. **다른 application service를 직접 호출하지 않음**(조합은 Facade, §6/§13). |
| **판별 기준** | "front-end가 요구하는 단일 use case인가?" → application service. |

- application은 thin해야 하고 규칙을 담지 않으며 트랜잭션을 조정한다 `[1]`(§1.2 인용). **[출처 확인]**
- Fowler의 Service Layer 정의 `[10]`:
  > "Defines an application's boundary with a layer of services that establishes a set of available operations and coordinates the application's response in each operation." `[10]` **[출처 확인]**
  > "encapsulates the application's business logic, controlling transactions and coordinating responses in the implementation of its operations." `[10]` **[출처 확인]**
- 결과는 Entity 그대로가 아니라 결과 DTO(Info)로 반환한다(Entity 외부 전파 차단 `[1]`). **[이 프로젝트 결정 — `[1]` 근거]**

> 참고(원전): "Application Service가 use case의 façade 역할"이라는 정식화는 Vaughn Vernon, *Implementing Domain-Driven Design* 의 권고. 책 원문 직접 fetch는 하지 않았다. **[차단 — 원전 미열람]**

---

## 6. Facade

| 항목 | 내용 |
|---|---|
| **정의** | **독립된 use case(= application service) 2개 이상**을 한 요청에 오케스트레이션하는 application 레이어 컴포넌트. |
| **책임** | 여러 application service 호출 순서 조정 + 오케스트레이션 트랜잭션 경계. |
| **두는 위치** | application |
| **판별 기준** | "독립적으로 떼어낼 수 있는 use case 2개+를 한 요청에 엮는가?" → Facade. **단일 read / 단일 use case에는 만들지 않는다.** |

- application service끼리 직접 호출을 금지하고, 여러 use case 조합은 Facade에만 두는 규칙은 **이 저장소의 로컬 관습**이다. GoF Facade는 일반 패턴이고 DDD 정식 building block은 아니다. **[이 프로젝트 결정]**
- Spring Data 문서도 "여러 repository를 아우르는 facade/service가 트랜잭션 경계를 정의한다"는 표현을 쓴다 `[11]`:
  > "use a facade or service implementation that (typically) covers more than one repository. Its purpose is to define transactional boundaries for non-CRUD operations." `[11]` **[출처 확인]**
- **YAGNI**: 단일 use case에 Facade를 선제적으로 만들지 않는다(이 저장소 설계 원칙: YAGNI → SOLID → Patterns). **[이 프로젝트 결정]**

---

## 7. Repository (port = domain / adapter = infrastructure)

| 항목 | 내용 |
|---|---|
| **정의** | 영속성 관심사를 도메인 모델 밖으로 분리하는 추상화. port(인터페이스)는 domain, 구현(adapter)은 infrastructure. |
| **책임** | aggregate root 단위의 조회/저장. |
| **두는 위치** | 인터페이스 → domain, 구현 → infrastructure |
| **판별 기준** | DIP: application/domain은 infrastructure 구현이 아니라 domain의 port에 의존. |

근거 `[7]`:
> "One or more persistence abstractions - interfaces - are defined in the domain model, and these abstractions have implementations in the form of persistence-specific adapters defined elsewhere in the application." `[7]` **[출처 확인]**
> "it's recommended that you define and place the repository interfaces in the domain model layer so the application layer ... doesn't depend directly on the infrastructure layer where you've implemented the actual repository classes." `[7]` **[출처 확인]**

aggregate root당 하나의 repository `[7]`:
> "For each aggregate or aggregate root, you should create one repository class." `[7]` **[출처 확인]**
> "you should never create a repository for each table in the database." `[7]` **[출처 확인]**

이 port-adapter 구조는 Hexagonal(Ports & Adapters) 아키텍처와 같은 의도(앱을 런타임 디바이스/DB로부터 격리)다.
단, Hexagonal 원문(`alistair.cockburn.us`)은 이 환경에서 **fetch가 차단**되어 직접 인용은 확인하지 못했다 `[9]`. **[차단]**
이 저장소는 layered + DIP(domain에 port, infrastructure에 adapter)를 채택하되, 정식 hexagonal port-adapter 명명을 전면 도입하지는 않는다. **[이 프로젝트 결정]**

---

## 8. 읽기 / 쿼리 조합

| 항목 | 내용 |
|---|---|
| **정의** | UI/클라이언트가 필요로 하는 데이터를 조회·조합하는 읽기 경로. |
| **두는 위치** | application (use case 흐름에서 조합) |
| **핵심 규칙** | 읽기는 **aggregate 경계를 무시하고 여러 aggregate/테이블을 조합**할 수 있다. |
| **판별 기준** | 쓰기(트랜잭션·도메인 규칙 준수) vs 읽기(idempotent, 도메인 규칙에서 분리). |

근거 `[12]`:
> "Writes execute transactions that must be compliant with the domain logic. Queries, on the other hand, are idempotent and can be segregated from the domain rules." `[12]` **[출처 확인]**
> "because you are creating queries independent of the domain model, the aggregates boundaries and constraints are ignored and you're free to query any table and column you might need." `[12]` **[출처 확인]**

읽기 결과는 클라이언트 전용 DTO(ViewModel)로 반환하며 도메인 모델 제약과 독립적이다 `[12]`:
> "the returned type can be specifically made for the clients ... The returned data (ViewModel) can be the result of joining data from multiple entities or tables in the database, or even across multiple aggregates." `[12]` **[출처 확인]**

> 이 저장소는 별도 CQRS 인프라(Dapper 등)를 도입하지 않고, application service의 읽기 메서드에서 여러 도메인 port를 조합하는 단순 접근을 쓴다. **[이 프로젝트 결정 — `[12]` 원칙을 단순화 적용]**

---

## 9. ErrorCode + 예외

| 항목 | 내용 |
|---|---|
| **정의** | 도메인별 에러 코드 enum + `CoreException` 서브클래스로 던지는 비즈니스/애플리케이션 실패. |
| **책임** | 도메인 실패를 식별 가능한 코드로 표현. HTTP 매핑은 공통 web advice가 담당. |
| **두는 위치** | ErrorCode enum → **owning 도메인** (application/interface 금지). 타 도메인은 import해 재사용. |
| **판별 기준** | 이 실패가 *어느 도메인의 규칙* 위반인가 → 그 도메인이 코드를 소유. |

- 도메인 에러 코드는 owning 도메인 모듈의 enum으로 정의하고 공통 `ErrorCode` 인터페이스를 구현한다.
  비즈니스 실패는 ad hoc `RuntimeException`이 아니라 `CoreException` 서브클래스(예: HTTP-semantic wrapper)로만 던진다. **[이 프로젝트 결정]**
- 새 공통 `ErrorCode` 인터페이스를 만들거나 앱마다 `CoreException`을 재정의하지 않는다. **[이 프로젝트 결정]**

> 이 패턴은 공식 스펙이 강제하는 것이 아니라 이 저장소의 에러 처리 규칙이다(`CLAUDE.md` §2). **[이 프로젝트 결정]**

---

## 10. DTO 경계 (Command / Info / 웹 DTO)

| DTO 종류 | 정의 | 두는 위치 | 판별 기준 |
|---|---|---|---|
| **Command** | 레이어/모듈 경계를 넘는 **입력** 모델 (interfaces → application) | application | use case 입력을 원시 파라미터 대신 묶어 전달할 때 |
| **Info** | application **결과** DTO (Entity를 그대로 노출하지 않기 위한 출력) | application | use case 결과를 반환할 때 |
| **웹 DTO** | HTTP 요청/응답 shape | interfaces | 외부 계약(JSON 바인딩/응답)을 표현할 때 |

- Entity를 presentation으로 전파하지 않기 위해 결과는 Info/DTO로 변환한다 `[1]`(§1.2 인용). **[출처 확인 — 변환 필요성] / [이 프로젝트 결정 — Command/Info 명명]**
- 읽기 ViewModel은 도메인 모델과 독립된 DTO다 `[12]`. **[출처 확인]**

---

## 11. Domain Event + Handler

| 항목 | 내용 |
|---|---|
| **정의** | 도메인에서 일어난 사실(과거형)을 같은 프로세스 내 다른 부분에 알리는 메시지. |
| **publish 위치** | application (use case 흐름에서 발행) |
| **handle 위치** | application (handler는 repository 등 인프라를 쓰므로 application 관심사) |
| **전파 정책** | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`. 본 트랜잭션 커밋 후 별도 트랜잭션에서 처리하고, 적재 실패는 본 트랜잭션과 독립(로깅만). |

근거 — 이벤트 핸들링은 application 관심사 `[13]`:
> "Handling the domain events is an application concern. The domain model layer should only focus on the domain logic ... the application layer level is where you should have domain event handlers." `[13]` **[출처 확인]**

근거 — 한 이벤트에 다수 핸들러, 0~n회 처리 가능 `[13]`:
> "A domain event could be processed zero or n times, because it can be received by multiple receivers or event handlers with a different purpose for each handler." `[13]` **[출처 확인]**

근거 — 커밋 전/후 dispatch가 부수효과를 같은 트랜잭션에 포함할지 별도 트랜잭션으로 둘지를 결정 `[13]`:
> "Deciding if you send the domain events right before or right after committing the transaction is important, since it determines whether you will include the side effects as part of the same transaction or in different transactions." `[13]` **[출처 확인]**

근거 — Spring `@TransactionalEventListener` 기본 phase는 `AFTER_COMMIT`, 트랜잭션 없으면 미실행 `[14]`:
> "The valid phases are BEFORE_COMMIT, AFTER_COMMIT (default), AFTER_ROLLBACK, as well as AFTER_COMPLETION" `[14]` **[출처 확인]**
> "If no transaction is running, the listener is not invoked at all" `[14]` **[출처 확인]**

> **AFTER_COMMIT + @Async + 적재 실패를 본 트랜잭션과 독립**으로 두는 조합은, "커밋 후 별도 트랜잭션 = 부수효과를 본 작업에서 분리"(`[13]`의 'after committing' = 'different transactions')를 Spring API로 구현한 것이다. 적재 실패를 삼키는(로깅만) 정책은 이 저장소의 결정이다. **[출처 확인 — 메커니즘] / [이 프로젝트 결정 — 실패 흡수 정책]**
>
> AFTER_COMMIT 리스너에 `@Transactional`을 붙일 때의 propagation 제약(REQUIRES_NEW 필요)은 별도 문서 `11-transactional-event-listener-propagation.md` 참조.

---

## 12. 트랜잭션 정책

| 작업 | 정책 | 근거 |
|---|---|---|
| **write** (생성/수정/삭제, 상태 변경) | application service/Facade에 `@Transactional` | 쓰기는 도메인 규칙 준수 트랜잭션 `[12]` **[출처 확인]** |
| **단일 쿼리 read** | service 레벨 `@Transactional` **불필요** | Spring Data repository가 이미 read 트랜잭션 경계 제공 `[11]` **[출처 확인]** |
| **여러 repository를 묶는 작업** | 바깥 service/Facade의 `@Transactional`가 경계 결정 | `[11]` **[출처 확인]** |

근거 `[11]`:
> "For read operations, the transaction configuration readOnly flag is set to true. All others are configured with a plain @Transactional so that default transaction configuration applies." `[11]` **[출처 확인]**
> "The transaction configuration at the repositories is then neglected, as the outer transaction configuration determines the actual one used." `[11]` **[출처 확인]**

- 트랜잭션은 use case/service 레벨에서 관리하고 repository에 `@Transactional`을 직접 부착하지 않는다(이 저장소 원칙). **[이 프로젝트 결정]**

---

## 13. "이 로직 어디 둘까" 결정 트리

```
이 로직은 ...
│
├─ 단일 Entity의 상태/불변식인가?
│     └─ 예 ──▶ Entity 메서드 (domain)
│
├─ 여러 aggregate에 걸친, 어느 Entity에도 안 붙는 stateless 도메인 규칙인가?
│     └─ 예 ──▶ Domain Service (domain)
│
├─ 단일 use case + 트랜잭션 경계인가?
│     └─ 예 ──▶ Application Service (application)
│
└─ 독립적으로 떼어낼 수 있는 use case(application service) 2개 이상을 한 요청에 엮는가?
      └─ 예 ──▶ Facade (application)

부가 판별:
- cross-domain 단순 READ(존재 확인 등) ──▶ 자기 application service가 다른 도메인 Repository port로 인라인
- 커밋 후 비동기 부수효과 ──▶ Domain Event publish(application) + @TransactionalEventListener(AFTER_COMMIT)+@Async handler
- 읽기 전용 조합(여러 aggregate) ──▶ application 레이어 읽기 경로(aggregate 경계 무시 가능)
```

판별 한 줄: **"이 작업을 그 도메인 application service의 단독 use case로 떼어낼 수 있나?"**
떼어낼 수 있는 게 2개+ → Facade. 떼면 어색한, 규칙에 묶인 stateless cross-aggregate 연산 → Domain Service.
단일 Entity 불변식 → Entity 메서드. 단순 read 가드 → port 인라인. **[이 프로젝트 결정 — §1~§12 근거의 종합]**

---

## 출처

| # | 자료 | URL | 확인 방식 |
|---|---|---|---|
| 1 | Microsoft .NET — Designing a DDD-oriented microservice (레이어/의존 방향) | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice | **직접 확인** (fetch, 인용 verbatim) |
| 2 | Microsoft .NET — Designing a microservice domain model (Entity/anemic) | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/microservice-domain-model | **직접 확인** (fetch) |
| 3 | Martin Fowler — ValueObject | https://martinfowler.com/bliki/ValueObject.html | **직접 확인** (fetch) |
| 4 | Microsoft .NET — Implementing value objects (no identity/immutable/value equality) | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/implement-value-objects | **직접 확인** (fetch) |
| 5 | Martin Fowler — DDD_Aggregate | https://martinfowler.com/bliki/DDD_Aggregate.html | **직접 확인** (fetch) |
| 6 | Martin Fowler — AnemicDomainModel | https://martinfowler.com/bliki/AnemicDomainModel.html | **직접 확인** (fetch) |
| 7 | Microsoft .NET — Infrastructure persistence layer (Repository port/adapter) | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/infrastructure-persistence-layer-design | **직접 확인** (fetch) |
| 8 | Robert C. Martin — The Clean Architecture (The Dependency Rule) | https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html | **직접 확인 불가(차단)** — 결론은 `[1]`로 보강 |
| 9 | Alistair Cockburn — Hexagonal (Ports & Adapters) | https://alistair.cockburn.us/hexagonal-architecture/ | **직접 확인 불가(차단)** — port/adapter 결론은 `[7]`로 보강 |
| 10 | Martin Fowler — Service Layer (PoEAA) | https://martinfowler.com/eaaCatalog/serviceLayer.html | **직접 확인** (fetch) |
| 11 | Spring Data JPA — Transactionality | https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html | **직접 확인** (fetch) |
| 12 | Microsoft .NET — Implementing reads/queries in CQRS (읽기 = aggregate 경계 무시) | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/cqrs-microservice-reads | **직접 확인** (fetch) |
| 13 | Microsoft .NET — Domain events: design and implementation | https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation | **직접 확인** (fetch) |
| 14 | Spring Framework — Transaction-bound Events (`@TransactionalEventListener`) | https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html | **직접 확인** (fetch) |
| — | Eric Evans, *Domain-Driven Design* (Entity/VO/Service/Repository 원전) | (서적) | **원전 미열람** — `[2][4][5][6]` 2차 인용으로 확인 |
| — | Vaughn Vernon, *Implementing Domain-Driven Design* (Application Service=façade, Aggregate rules) | (서적) | **원전 미열람** — 표기만 |

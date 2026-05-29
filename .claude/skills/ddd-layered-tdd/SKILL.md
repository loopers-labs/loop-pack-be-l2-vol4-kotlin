---
name: ddd-layered-tdd
description: |
  Loopers commerce-api(및 동류의 Kotlin/Spring DDD 프로젝트)에서 기능을 DDD layered
  architecture(interfaces → application → domain ← infrastructure)로, TDD(red-green-refactor)로
  구현할 때 사용한다. 새 도메인/Entity/VO/Service/Repository/Controller를 만들거나,
  "이 로직을 Application Service / Domain Service / Facade 중 어디에 둘까", "cross-domain 호출을
  어떻게 할까", "응답 DTO를 어디서 변환할까"를 결정할 때 활성화한다.
  트리거: "이 스킬로", "$ddd-layered-tdd", "도메인 구현", "Brand/Product/Like/Order 구현",
  "Phase N 진행", "레이어 어디에 둘까", "Facade 필요해?", "Domain Service 만들까", "commerce-api 도메인 코드".
  TDD 케이스 설계의 깊이가 필요하면 kent-beck-tdd 스킬을 함께 사용한다.
---

# DDD Layered Architecture + TDD 구현 스킬

> 이 스킬은 loopers `loop-pack-be-l2-vol4-kotlin`에서 합의된 레이어 규칙을 코드로 옮기는 절차다.
> 충돌 시 우선순위: 사용자 명시 지시 > 이 저장소 `CLAUDE.md` / `docs/week3/*` > 이 스킬.
> 톤은 항상 존댓말. 약어 클래스/컴포넌트명 금지(풀네임만) — `docs/ubiquitous-language.md` §9.

## 0. 절대 규칙 (어기지 말 것)

1. **테스트 리스트를 사용자가 confirm 하기 전에는 production/test 코드를 쓰지 않는다.** 리스트 먼저 → confirm → red → green → refactor. (case 설계는 kent-beck-tdd)
2. **의존 방향(불변)**: `interfaces → application → domain ← infrastructure`. **domain은 application·infrastructure를 절대 의존/import 하지 않는다.** infrastructure가 domain의 port를 구현한다(DIP). (단 domain은 절충안상 `@Entity` 때문에 `jakarta.persistence` + `persistence-core`(BaseEntity) + `supports:error`(ErrorCode)까지는 의존한다 — **"방향이 불변"이 핵심이지 "domain 의존 0"이 핵심이 아니다.**)
3. **application service는 다른 application service를 호출하지 않는다.** 여러 use case 조합은 **Facade만** 한다.
4. **domain service는 다른 도메인의 application service를 호출하지 않는다.** 다른 도메인의 **Entity + Repository 포트 + 도메인 메서드**만 조합한다.
5. **컨트롤러는 Entity를 보지 않는다.** application은 `{D}Info`를, 컨트롤러는 `{D}V1Dto`를 다룬다.
6. **투기적 레이어 금지(YAGNI).** Domain Service / Facade는 정당화되는 순간에만 만든다. 단일 entity CRUD에는 Domain Service를 만들지 않는다.

## 1. 레이어와 패키지

```
apps/commerce-api/src/main/kotlin/com/loopers/
  interfaces/api/{도메인}/   # Presentation: {D}V1Controller, Admin{D}V1Controller, {D}V1Dto, {D}V1ApiSpec
  application/{도메인}/        # Application: {D}Service (+ {D}Facade는 정당할 때만), Command/Info 동거
  domain/{도메인}/             # Domain ★: Entity, VO(@Embeddable), {D}Repository(port), {D}ErrorCode, (Domain Service)
  infrastructure/{도메인}/     # Infrastructure: {D}JpaRepository(:JpaRepository), {D}RepositoryImpl(@Component)
```

- **Repository port 인터페이스는 `domain/{도메인}/`** 에 둔다 (account 모듈과 의도적으로 다름 — account는 persistence에 둠).
- **Spring Data JPA 인터페이스 + Impl은 반드시 `com.loopers.infrastructure.{도메인}` 아래.** `JpaConfig`의 `@EnableJpaRepositories(basePackages=["com.loopers.infrastructure"])` 제약. account처럼 별도 PersistenceConfig를 만들지 않는다(JpaConfig가 이미 스캔).
- Entity는 `@EntityScan(["com.loopers"])`라 위치 자유지만 `domain/{도메인}/`에 둔다.

### 1.1 모듈 분리로의 확장 (지금은 단일 앱, 미래 대비 규율)

- 현재는 단일 앱 + 패키지 분리지만 **패키지 의존 방향 = 모듈 의존 방향**이다. 모듈로 쪼개면:
  ```
  commerce-interfaces → commerce-application → commerce-domain ← commerce-infrastructure
  ```
  즉 **infrastructure 모듈이 domain 모듈을 의존한다**(domain의 port를 구현 + entity 사용). 역방향 없음. compile 의존(infra→domain)은 런타임 호출(application→port)과 방향이 반대 — 그게 DIP.
- 이걸 가능케 하는 **단 하나의 규율**: **domain 패키지는 infrastructure/application 패키지를 import 하지 않는다.** 이것만 지키면 패키지 → 모듈 전환이 기계적이다. 한 번이라도 domain이 infra를 import하면 모듈화가 불가능해진다.
- 모듈로 가도 domain은 §0.2대로 `jakarta.persistence` + `persistence-core` + `supports:error`까지만 의존(절충안). Spring Data JPA / 다른 도메인 / infrastructure는 모름.
- 참고로 account 모듈은 port를 `persistence`에 둬서 `application → account-persistence` 의존이 생긴다(layered 단순화). commerce는 port를 `domain`에 둬 `application → domain`, `infrastructure → domain`으로 더 정석(DIP). 둘 다 `infra/persistence → domain` 방향은 성립.

## 2. "어디에 둘까" 결정 트리 (이 스킬의 핵심)

작업 하나를 받으면 이 순서로 판단한다.

```
이 행위가 단일 Entity/VO의 상태·invariant인가?
  └ 예 → Entity/VO의 메서드 (도메인). 예: BrandName.init 검증, inventory.decrease(n), product.like()
  └ 아니오 ↓
여러 Aggregate에 걸친 "도메인 규칙이 있는" 행위인가? (Evans 3조건: 어느 Entity에도 안 붙음 / 도메인 객체로 표현 / stateless)
  └ 예 → Domain Service (domain/). 도메인 객체·포트만 조합. 예: OrderPlacementService(주문 체결)
  └ 아니오 ↓
한 도메인의 use case + 트랜잭션 경계인가?
  └ 예 → Application Service (application/). 항상 여기. cross-domain READ는 포트로 인라인 허용
  └ 아니오 ↓
서로 독립적인 use case(각자 app service 소유)를 여러 개 한 요청에 엮는가?
  └ 예 → Facade (application/). app service들 조합. 예: 주문+결제+쿠폰+배송
```

### 세 Service 구분 한 표

| | 조합 단위 | 반환 | 트랜잭션 | 다른 도메인 접근 |
|---|---|---|---|---|
| **Domain Service** | 도메인 **객체**(Entity/VO/port) → 하나의 도메인 연산 | 도메인 객체 | 없음 | 다른 도메인 **Repository 포트 + 도메인 메서드만** (app service ❌) |
| **Application Service** | 자기 도메인 use case 1개 | `{D}Info`/DTO | `@Transactional` 소유 | cross-domain **READ는 포트로 인라인 OK**, 다른 app service 호출 ❌ |
| **Facade** | **use case**(app service) 여러 개 | `{D}Info`/DTO | 오케스트레이션 경계 | 여러 app service 호출 가능(유일하게 허용된 자리) |

## 3. cross-domain 규칙 (가장 헷갈리는 지점)

기준은 "**건드리는 도메인 개수**"나 "**write 개수**"가 **아니라** "**조합하는 단위가 독립 use case인가**"다.

| 상황 | 판정 | 근거 |
|---|---|---|
| 주문 시 제품 조회 (read) | 인라인 read — `OrderPlacementService`(domain)가 `ProductRepository`(포트)로 읽음 | read는 use case 조합 아님 |
| 상품 생성 시 브랜드 존재 검증 (read guard) | `ProductService`(app)가 `BrandRepository`로 `existsById`. Facade로 안 올림 | 읽기 전용 가드 |
| 주문 시 재고 차감 (write, 규칙에 묶임) | `OrderPlacementService`가 `inventory.decrease(n)` 도메인 메서드 호출 | "주문 체결" 규칙의 일부, 떼면 어색 → Domain Service |
| 주문 + 결제 (독립 use case 2개) | **Facade**가 `OrderService` + `PaymentService` 조합 | 결제는 외부 연동·독자 정책 가진 완결 use case |

판별 한 줄: **"이 작업을 그 도메인 app service의 단독 use case로 떼어낼 수 있나?" 떼어낼 수 있는 게 2개+ → Facade. 떼면 어색한 규칙-묶인 상태변경 → Domain Service. 단순 read → 인라인(포트).**

- READ에서 write가 시작되면 더 이상 read가 아니다 — 위 규칙으로 재판단.
- cross-domain은 **항상 다른 도메인의 Repository 포트**로 (그 도메인 app service 호출 ❌, 테이블 직접 쿼리 ❌).
- 도메인 간 **DB FK 없음** — soft reference(id). Aggregate **안**=객체참조+JPA cascade, **사이**=id 참조. cascade는 application에서 명시 호출(그게 여러 app service write를 엮으면 Facade 후보).

## 4. DTO 경계

| 레이어 | 클래스 | 책임 | 변환 위치 |
|---|---|---|---|
| domain | `Brand`(Entity), `BrandName`(VO) | 상태 + 비즈니스 메서드 + invariant | — |
| application | **`{D}Info`** (예 `BrandInfo`) | 도메인 수행 결과 DTO. Entity 그대로 내보내지 않음 | Entity → Info: **Service에서** |
| interfaces | **`{D}V1Dto`**(Request/Response) | 웹 계약(버전된 요청/응답) | Info → Response: **컨트롤러에서** |

- 컨트롤러는 `ApiResponse`를 직접 반환하지 않는다 — 도메인 DTO 또는 no body 반환 → `ApiResponseBodyAdvice`가 래핑.
- Command는 모듈/레이어 경계를 넘는 입력 모델일 때 사용(`{D}CreateCommand`). 단일 컨트롤러 전용 Request DTO는 컨트롤러 파일에 동거.

## 5. Entity / VO 규칙

- VO는 `@Embeddable` + `init`에서 **길이 + 형식 모두** 검증(fail-fast). 위반 시 `BadRequestException({D}ErrorCode.INVALID_XXX)`.
- **VO는 불변 값 개념에만** (검증된 이름·화폐 등). **단일 카운터(좋아요 수·재고 수량)는 VO가 아니라 엔티티 필드 + 도메인 메서드**로 둔다 — 가변 in-place 증감은 VO(불변)에 안 맞고 값으로 다뤄지지 않음. 예: `Product.likeCount: Long` + `like()/unlike()`, `Inventory.quantity: Long` + `decrease()/increase()`.
- **화폐는 단일 `Money` VO**(shared kernel, `com.loopers.domain.shared`)로 Product 가격·Order 금액 모두 표현 — 별도 `Price` VO 두지 않음. 두 도메인이 서로 의존하지 않도록 shared 패키지에 둔다(둘 다 `domain.shared → ` 의존, domain 간 직접 의존 아님).
- 컬럼 길이 상한은 `@Column(length=..)`와 `init` 양쪽에 **직접 숫자**(`MAX_LENGTH` 상수화 금지 — 한 클래스에서만 쓰면 literal이 더 명확).
- VO에 `equals`/`hashCode`/`toString` 구현. `toString`은 **원문 value 그대로**(도메인에 마스킹 정책 박지 않음). 앱 코드에서 VO를 `"$vo"`/`logger.debug("{}", value)`로 로깅하지 말 것(PII 우회).
- Entity는 `BaseEntity` 상속 — `id`/`createdAt`/`updatedAt`/`deletedAt` + 멱등 `delete()`/`restore()` 제공.
- **soft delete는 `deletedAt`만 사용. `status: ACTIVE/DELETED` enum을 새로 만들지 않는다**(BaseEntity 빌트인 중복).
- 도메인 throw 시 `customMessage`에 사용자 입력값(이메일/loginId 등) 끼워넣지 않는다(PII 누수).

## 6. 에러 / 응답 / 인증

- 비즈니스/애플리케이션 실패는 **`CoreException` 서브클래스만**: `BadRequestException` / `UnauthorizedException` / `ForbiddenException` / `NotFoundException` / `ConflictException` / `InternalServerException`. ad hoc `RuntimeException` 금지.
- 도메인 에러 코드는 **owning 도메인 enum**에 정의, `ErrorCode` 구현, `code = "{DOMAIN}:$name"`. 다른 도메인이 throw할 땐 **import해서 재사용**(중복 정의 금지). 예: `ProductService`가 brand 미존재 시 `NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)`.
- 인증: 이 앱은 `account-security`를 재사용한다(필터 신설 ❌). 인증된 사용자 식별자는 **`RequestAttribute("accountId")`** — 컨트롤러가 읽어 본인 검증(Like 403 / Order 404)·하위 전달에 사용. 외부 path의 `userId` == 내부 `accountId`.

## 7. TDD 워크플로 (도메인 하나 구현 순서)

각 단계는 red → green → refactor. 테스트 리스트는 먼저 confirm.

1. **테스트 리스트 작성 → 사용자 confirm** (kent-beck-tdd로 MECE·경계 중심 케이스 설계).
2. **VO invariant** (`{VO}Test`, 단위) → VO 구현.
3. **Entity 비즈니스 메서드** (`{D}Test`, 단위) → Entity 구현.
4. **(필요하면) Domain Service** (`{D}DomainServiceTest`, mock으로 포트 합성 검증) → 구현.
5. **Application Service** (`{D}ServiceTest`, Repository 포트 + 협력자 mock) → 구현. 트랜잭션 경계·use case 흐름.
6. **Repository** (`{D}RepositoryIntegrationTest`, `@DataJpaTest` + H2) → port + Impl + JpaRepository.
7. **Controller** (`{D}V1ControllerE2ETest`, `@SpringBootTest` + `@AutoConfigureMockMvc` + `DatabaseCleanup`) → 컨트롤러 + Dto + ApiSpec.
8. phase 끝: `./gradlew :apps:commerce-api:test --tests '*{D}*'` + `:apps:commerce-api:ktlintCheck` 통과.

### 테스트 레벨 규칙

| 종류 | 도구 | 파일명 |
|---|---|---|
| Domain 단위 | Spring 없이 순수 JUnit (+ MockK) | `*Test.kt` |
| Repository 통합 | `@DataJpaTest` + 임베디드 H2 (MySQL 전용 동작 검증 시에만 Testcontainers) | `*IntegrationTest.kt` |
| Controller E2E | `@SpringBootTest` + MockMvc + `DatabaseCleanup` `@BeforeEach` | `*E2ETest.kt` |

- **`@SpringBootTest`에 `@Transactional` 일괄 부착 금지.** `DatabaseCleanup`을 `@BeforeEach`로 호출(propagation/flush/lazy/race 함정 회피).
- E2E `@DisplayName`에 시나리오 ID(`B-R1`, `O-C4` 등)를 그대로 인용 → 평가 trace.
- 서비스 테스트는 Repository 포트·협력자를 mock. domain service 테스트는 Spring 없이.

## 8. 안티패턴 즉시 정지 체크리스트

- [ ] application service가 다른 application service를 호출 → **Facade로 빼라**
- [ ] domain service가 다른 도메인 application service를 호출 → **Repository 포트/도메인 메서드로 바꿔라** (예: `InventoryService` ❌ → `InventoryRepository`/`inventory.decrease()`)
- [ ] 컨트롤러가 Entity를 직접 반환/수신 → `{D}Info`/`{D}V1Dto` 경유
- [ ] "혹시 몰라서" Facade/Domain Service 신설 → 정당화(독립 use case 2개+ / 도메인 규칙 있는 cross-aggregate) 없으면 만들지 마라
- [ ] `status` enum으로 soft delete → `deletedAt`만
- [ ] `RuntimeException` / 앱 로컬 `CoreException` 재정의 / `ErrorType` 도입 → 금지
- [ ] 약어 클래스명(`Ctrl`/`Svc`/`Repo`/`Mgr`/`Resp` 등) → 풀네임만
- [ ] Spring Data JPA 인터페이스가 `com.loopers.infrastructure` 밖 → 빈 등록 안 됨
- [ ] **domain 패키지가 infrastructure/application을 import** → 의존 방향 위반 + 모듈화 영구 불가. 즉시 제거(domain은 port만 노출, 구현은 infra가 가져감)
- [ ] 단일 구현체를 위한 인터페이스 → 만들지 마라

## 9. 프로젝트 바인딩 (이 저장소 파일 위치)

- BaseEntity: `modules/persistence-core/src/main/kotlin/com/loopers/domain/BaseEntity.kt`
- JpaConfig(스캔 제약): `modules/jpa/src/main/kotlin/com/loopers/config/jpa/JpaConfig.kt`
- 예외 베이스: `supports/error/.../CoreException.kt` / 매핑: `supports/web/.../ApiControllerAdvice.kt`
- 응답 래핑: `supports/web/.../ApiResponseBodyAdvice.kt`
- 테스트 cleanup: `apps/commerce-api/src/test/kotlin/com/loopers/support/DatabaseCleanup.kt`
- 참조 구현체(DDD layered): `modules/account-*` (단, Repository 포트 위치는 이 스킬 §1대로 domain에 — account와 다름)
- 계획/체크리스트: `docs/week3/00-plan.md`, `docs/week3/01-checklist.md`
- 개념 가이드: `docs/week3/02-ddd-concepts.html`, 아키텍처: `docs/week3/03-architecture.html`
- 어휘/약어/호칭: `docs/ubiquitous-language.md`

## 10. bootRun / 명령

- 빌드/테스트: `./gradlew :apps:commerce-api:test`, 포맷: `:apps:commerce-api:ktlintCheck` / `:ktlintFormat`
- 기동: `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'` (프로파일 항상 명시)

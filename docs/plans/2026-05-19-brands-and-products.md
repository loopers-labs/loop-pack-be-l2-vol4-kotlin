# Round 2 — 브랜드 & 상품 구현 계획

> **에이전트 안내:** 이 계획을 task 단위로 실행할 때는 `superpowers:subagent-driven-development` 또는 `superpowers:executing-plans` 스킬을 사용하세요. 체크박스(`- [ ]`)는 진행 상황 추적용입니다.
>
> **개발자 안내:** `CLAUDE.md`의 TDD/3A/`CoreException` 규약과 패키지 레이어링(`domain → application → interfaces`, 영속성은 `infrastructure`)을 그대로 따릅니다. 한 체크박스 = 하나의 작은 동작(Red) + 그것을 통과시키는 최소 구현(Green). Refactor는 관련 테스트가 모두 통과한 상태에서만 수행합니다.

**Goal:** 브랜드와 상품 카탈로그(고객/어드민)를 도메인 주도 설계로 구현한다. 회원이 브랜드와 상품을 조회할 수 있고, 어드민이 브랜드/상품을 등록·수정·삭제할 수 있다.

**Architecture:** Round 1 의 `user` 도메인과 동일한 4-Layer DDD (`domain / application / interfaces / infrastructure`). 브랜드와 상품은 별도 Aggregate. 상품은 브랜드 ID 만 참조하고(엔티티 참조 금지) Eager join 을 피한다. 브랜드 삭제 시 상품도 삭제(애플리케이션 레이어에서 명시적 cascade — DB FK cascade 에 의존하지 않음). 어드민은 `/api-admin/v1` prefix 와 `X-Loopers-Ldap` 헤더로 식별한다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, Spring Data JPA, Testcontainers(MySQL 8), JUnit 5, AssertJ, ktlint. (기존 멀티 모듈 그대로.)

---

## 0. 도메인/모델 결정사항

### Brand
- 필드: `id`(BaseEntity), `name`(String, NOT NULL), `description`(String, NOT NULL, 빈 문자열 허용)
- 이름 길이: 1~50자. 공백만은 거부.
- `name` 유일성: **검증하지 않음** (요구사항에 없음).
- 삭제: 하드 삭제. 삭제 시 동일 트랜잭션에서 해당 브랜드의 상품들도 삭제(애플리케이션 레이어 책임).

### Product
- 필드: `id`, `brandId`(Long, NOT NULL), `name`(String, NOT NULL), `description`(String, NOT NULL, 빈 문자열 허용), `price`(Long, NOT NULL, ≥ 0, 원 단위), `stock`(Int, NOT NULL, ≥ 0)
- 브랜드 참조: 엔티티가 아닌 `brandId: Long` 만 보관(Aggregate 경계). 등록·수정 시 브랜드 존재는 `ProductService` 에서 `BrandRepository.existsById` 로 확인.
- 수정: 브랜드는 변경 불가(요구사항). 수정 가능 필드는 `name / description / price / stock`.
- 좋아요 카운트 / 재고 차감 / 상품 스냅샷은 본 계획 범위 **외** (Round 3, 4 에서 추가).

### 정렬(Customer 상품 목록)
- 본 계획은 `latest` 와 `price_asc` 만 구현. `likes_desc` 는 Round 3 (Likes) 에서 추가.
- `latest`: `ORDER BY id DESC` (BaseEntity 의 createdAt 이 동일 시점일 때 안정 정렬 보장 어려움 → id 기준).
- `price_asc`: `ORDER BY price ASC, id ASC`.
- 미지원/누락 정렬값은 `latest` 로 폴백(예외 X). 잘못된 enum 문자열은 400.

### 페이지네이션
- `page` 기본 0, `size` 기본 20, 최대 100 으로 제한(서버 보호).

### 어드민 인증
- `HandlerInterceptor` 로 `/api-admin/**` 에 대해 `X-Loopers-Ldap == "loopers.admin"` 검증. 불일치/누락 시 `CoreException(ErrorType.FORBIDDEN)`.
- `FORBIDDEN(HttpStatus.FORBIDDEN, ...)` 를 `ErrorType` 에 추가.

### 응답 노출 정책
- 고객(`/api/v1`):
  - 브랜드: `id, name, description`
  - 상품: `id, brandId, name, description, price, stock`
- 어드민(`/api-admin/v1`): 위 필드에 더해 `createdAt, updatedAt` 포함.

---

## 1. 파일 구조

신규/수정 파일 한눈에. 각 파일은 단일 책임을 갖는다.

```
apps/commerce-api/src/main/kotlin/com/loopers
├── support/error/ErrorType.kt                       (modify: FORBIDDEN 추가)
├── interfaces/api/admin/AdminAuthInterceptor.kt     (create)
├── interfaces/api/admin/AdminWebMvcConfig.kt        (create)
│
├── domain/brand/BrandModel.kt                       (create)
├── domain/brand/BrandRepository.kt                  (create)
├── domain/brand/BrandService.kt                     (create)
├── application/brand/BrandFacade.kt                 (create)
├── application/brand/BrandInfo.kt                   (create)
├── infrastructure/brand/BrandJpaRepository.kt       (create)
├── infrastructure/brand/BrandRepositoryImpl.kt      (create)
├── interfaces/api/brand/BrandV1Controller.kt        (create)
├── interfaces/api/brand/BrandV1Dto.kt               (create)
├── interfaces/admin/brand/BrandAdminV1Controller.kt (create)
├── interfaces/admin/brand/BrandAdminV1Dto.kt        (create)
│
├── domain/product/ProductModel.kt                   (create)
├── domain/product/ProductRepository.kt              (create)
├── domain/product/ProductService.kt                 (create)
├── domain/product/ProductSort.kt                    (create)
├── application/product/ProductFacade.kt             (create)
├── application/product/ProductInfo.kt               (create)
├── infrastructure/product/ProductJpaRepository.kt   (create)
├── infrastructure/product/ProductRepositoryImpl.kt  (create)
├── interfaces/api/product/ProductV1Controller.kt    (create)
├── interfaces/api/product/ProductV1Dto.kt           (create)
├── interfaces/admin/product/ProductAdminV1Controller.kt (create)
└── interfaces/admin/product/ProductAdminV1Dto.kt    (create)

apps/commerce-api/src/test/kotlin/com/loopers
├── domain/brand/BrandModelTest.kt                   (create, 단위)
├── domain/brand/BrandServiceTest.kt                 (create, 단위 + InMemoryRepo)
├── domain/brand/BrandServiceIntegrationTest.kt      (create, @SpringBootTest)
├── domain/product/ProductModelTest.kt               (create, 단위)
├── domain/product/ProductServiceTest.kt             (create, 단위)
├── domain/product/ProductServiceIntegrationTest.kt  (create, @SpringBootTest)
├── interfaces/api/BrandV1ApiE2ETest.kt              (create, E2E)
├── interfaces/api/ProductV1ApiE2ETest.kt            (create, E2E)
├── interfaces/admin/BrandAdminV1ApiE2ETest.kt       (create, E2E)
└── interfaces/admin/ProductAdminV1ApiE2ETest.kt     (create, E2E)

http/commerce-api
├── brand-v1.http                                    (create)
├── product-v1.http                                  (create)
├── brand-admin-v1.http                              (create)
└── product-admin-v1.http                            (create)
```

---

## Task 1. 어드민 인증 인터셉터 + `FORBIDDEN` 도입

**Files**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/AdminAuthInterceptor.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/admin/AdminWebMvcConfig.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/admin/AdminAuthInterceptorE2ETest.kt`

체크리스트:

- [ ] **Red(단위 → 도메인 없음, 바로 E2E):** `X-Loopers-Ldap` 헤더 없이 `/api-admin/v1/brands` 요청 시 403 응답.

  ```kotlin
  // BrandAdminV1ApiE2ETest 와 별개로 인터셉터 자체를 검증하는 미니 E2E
  @SpringBootTest(webEnvironment = RANDOM_PORT)
  class AdminAuthInterceptorE2ETest @Autowired constructor(
      private val rest: TestRestTemplate,
  ) {
      @DisplayName("X-Loopers-Ldap 헤더가 없으면 어드민 엔드포인트는 403 을 반환한다.")
      @Test
      fun returnsForbidden_whenLdapHeaderMissing() {
          val response = rest.exchange("/api-admin/v1/brands", HttpMethod.GET, HttpEntity<Any>(HttpHeaders()), ApiResponse::class.java)
          assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
      }

      @DisplayName("X-Loopers-Ldap 헤더 값이 loopers.admin 이 아니면 403 을 반환한다.")
      @Test
      fun returnsForbidden_whenLdapHeaderInvalid() {
          val headers = HttpHeaders().apply { set("X-Loopers-Ldap", "loopers.guest") }
          val response = rest.exchange("/api-admin/v1/brands", HttpMethod.GET, HttpEntity<Any>(headers), ApiResponse::class.java)
          assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
      }
  }
  ```

  실행: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.admin.AdminAuthInterceptorE2ETest"`
  예상: 실패 (엔드포인트 미존재 → 404 또는 200 등). NOT 403.

- [ ] **Green:** `ErrorType.FORBIDDEN` 추가.

  ```kotlin
  FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.reasonPhrase, "접근 권한이 없습니다."),
  ```

- [ ] **Green:** `AdminAuthInterceptor` 작성.

  ```kotlin
  package com.loopers.interfaces.api.admin

  import com.loopers.support.error.CoreException
  import com.loopers.support.error.ErrorType
  import jakarta.servlet.http.HttpServletRequest
  import jakarta.servlet.http.HttpServletResponse
  import org.springframework.stereotype.Component
  import org.springframework.web.servlet.HandlerInterceptor

  @Component
  class AdminAuthInterceptor : HandlerInterceptor {
      companion object {
          const val HEADER = "X-Loopers-Ldap"
          const val ALLOWED = "loopers.admin"
      }

      override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
          val ldap = request.getHeader(HEADER)
          if (ldap != ALLOWED) {
              throw CoreException(ErrorType.FORBIDDEN, "어드민 권한이 없습니다.")
          }
          return true
      }
  }
  ```

- [ ] **Green:** `AdminWebMvcConfig` 로 `/api-admin/**` 등록.

  ```kotlin
  package com.loopers.interfaces.api.admin

  import org.springframework.context.annotation.Configuration
  import org.springframework.web.servlet.config.annotation.InterceptorRegistry
  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

  @Configuration
  class AdminWebMvcConfig(
      private val adminAuthInterceptor: AdminAuthInterceptor,
  ) : WebMvcConfigurer {
      override fun addInterceptors(registry: InterceptorRegistry) {
          registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api-admin/**")
      }
  }
  ```

  주의: 이 시점에는 어떤 어드민 컨트롤러도 없어서 두 테스트가 404 를 받을 수 있다. **`AdminAuthInterceptor` 는 핸들러 매핑 이전이 아니라 이후에 실행**되므로 매핑되지 않은 경로에 대해서는 `NoResourceFoundException` → 404 가 먼저 반환된다. 따라서 본 테스트의 Green 은 Task 4(어드민 브랜드 컨트롤러 등록) 직후에 비로소 통과한다.

  ⚠️ **결정:** 본 테스트는 **Task 4 마지막 단계에서 통과시킨다.** 본 Task 에서는 `@Disabled("Task 4 에서 어드민 라우트 등록 후 활성화")` 어노테이션을 잠시 부여하고, Task 4 끝에서 제거한다. 그동안 인터셉터 동작은 Task 4 의 어드민 E2E 가 함께 검증한다.

- [ ] **Commit:** `git add ... && git commit -m "feat: add ErrorType.FORBIDDEN and admin auth interceptor"`

---

## Task 2. 브랜드 도메인 모델 + 영속성

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandModel.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/brand/BrandRepositoryImpl.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandModelTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt`

체크리스트:

- [ ] **Red(단위):** 유효한 이름과 설명으로 브랜드 생성. 이름이 빈 문자열/공백/51자 이상이면 `CoreException(BAD_REQUEST)`.

  ```kotlin
  // BrandModelTest.kt
  @DisplayName("브랜드를 생성할 때,")
  @Nested
  inner class Create {
      @DisplayName("유효한 이름과 설명이면 생성된다.")
      @Test
      fun createsBrand_whenValid() {
          val brand = BrandModel(name = "Nike", description = "Just do it.")
          assertThat(brand.name).isEqualTo("Nike")
      }

      @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다.")
      @Test
      fun throwsBadRequest_whenNameBlank() {
          val exception = assertThrows<CoreException> { BrandModel(name = "   ", description = "") }
          assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
      }

      @DisplayName("이름이 50자를 초과하면 BAD_REQUEST 예외가 발생한다.")
      @Test
      fun throwsBadRequest_whenNameTooLong() {
          val exception = assertThrows<CoreException> { BrandModel(name = "a".repeat(51), description = "") }
          assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
      }
  }
  ```

- [ ] **Green:** `BrandModel` 구현. `UserModel` 의 패턴(생성자 인자 → `init` validate, `protected set`, BaseEntity 상속, `@Entity @Table(name="brands")`)을 그대로 따른다.

- [ ] **Red(단위):** `BrandService.create / get / update / delete` 동작을 In-Memory Repo 로 검증.

  ```kotlin
  // BrandServiceTest.kt
  @DisplayName("브랜드 상세를 조회할 때,")
  @Nested
  inner class GetById {
      @DisplayName("존재하지 않는 브랜드 ID 이면 NOT_FOUND 예외가 발생한다.")
      @Test
      fun throwsNotFound_whenBrandMissing() {
          val service = BrandService(InMemoryBrandRepository())
          val exception = assertThrows<CoreException> { service.getById(999L) }
          assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
      }
  }

  @DisplayName("브랜드를 삭제할 때,")
  @Nested
  inner class Delete {
      @DisplayName("존재하지 않는 브랜드 ID 이면 NOT_FOUND 예외가 발생한다.")
      @Test
      fun throwsNotFound_whenBrandMissing() {
          val service = BrandService(InMemoryBrandRepository())
          val exception = assertThrows<CoreException> { service.delete(999L) }
          assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
      }
  }
  ```

- [ ] **Green:** `BrandRepository` 인터페이스 + `BrandService` 구현.

  ```kotlin
  // BrandRepository.kt
  interface BrandRepository {
      fun save(brand: BrandModel): BrandModel
      fun findById(id: Long): BrandModel?
      fun existsById(id: Long): Boolean
      fun findAll(pageable: Pageable): Page<BrandModel>
      fun delete(brand: BrandModel)
  }
  ```

  ```kotlin
  // BrandService.kt — 노출 시그니처만 표시
  @Component
  class BrandService(private val brandRepository: BrandRepository) {
      @Transactional fun create(command: CreateBrandCommand): BrandModel
      @Transactional(readOnly = true) fun getById(id: Long): BrandModel
      @Transactional(readOnly = true) fun list(pageable: Pageable): Page<BrandModel>
      @Transactional fun update(id: Long, command: UpdateBrandCommand): BrandModel
      @Transactional fun delete(id: Long)
      data class CreateBrandCommand(val name: String, val description: String)
      data class UpdateBrandCommand(val name: String, val description: String)
  }
  ```

  `delete` 의 cascade(연관 상품 제거)는 **Task 8** 에서 추가한다. 본 Task 의 `delete` 는 단순 브랜드 삭제만 수행.

- [ ] **Red(통합):** `@SpringBootTest` + Testcontainers 로 저장 후 조회 검증.

  ```kotlin
  // BrandServiceIntegrationTest.kt
  @DisplayName("브랜드를 등록하면 DB 에 저장된다.")
  @Test
  fun savesBrand() {
      val brand = brandService.create(BrandService.CreateBrandCommand(name = "Nike", description = "Just do it."))
      val found = brandJpaRepository.findById(brand.id).orElse(null)
      assertThat(found).isNotNull
      assertThat(found?.name).isEqualTo("Nike")
  }
  ```

  `@AfterEach databaseCleanUp.truncateAllTables()` 패턴은 `UserServiceIntegrationTest` 와 동일.

- [ ] **Green:** `BrandJpaRepository : JpaRepository<BrandModel, Long>` 와 `BrandRepositoryImpl : BrandRepository` 작성. `findAll(pageable)` 은 `JpaRepository` 의 기본 메서드 사용.

- [ ] **Run all:** `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.*"`
  예상: 모두 통과.

- [ ] **Commit:** `feat: add brand domain model, service, repository`

---

## Task 3. 브랜드 고객 API — `GET /api/v1/brands/{brandId}`

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandFacade.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/brand/BrandV1Dto.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/BrandV1ApiE2ETest.kt`
- Create: `http/commerce-api/brand-v1.http`

체크리스트:

- [ ] **Red(E2E):** 존재하는 브랜드 조회 시 200 + `id/name/description`. 없는 ID 면 404.

  ```kotlin
  @DisplayName("GET /api/v1/brands/{brandId}")
  @Nested
  inner class GetBrand {
      @DisplayName("존재하는 브랜드 ID 이면 브랜드 정보를 반환한다.")
      @Test
      fun returnsBrand_whenExists() {
          val saved = brandJpaRepository.save(BrandModel(name = "Nike", description = "Just do it."))
          val type = object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {}
          val response = rest.exchange("/api/v1/brands/${saved.id}", HttpMethod.GET, null, type)
          assertAll(
              { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
              { assertThat(response.body?.data?.id).isEqualTo(saved.id) },
              { assertThat(response.body?.data?.name).isEqualTo("Nike") },
          )
      }

      @DisplayName("존재하지 않는 브랜드 ID 이면 404 를 반환한다.")
      @Test
      fun returnsNotFound_whenMissing() {
          val response = rest.exchange("/api/v1/brands/9999", HttpMethod.GET, null, ApiResponse::class.java)
          assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
      }
  }
  ```

- [ ] **Green:**
  - `BrandFacade.getById(id) -> BrandInfo`
  - `BrandInfo.from(BrandModel)` (id/name/description 만)
  - `BrandV1Controller @GetMapping("/{brandId}")`
  - `BrandV1Dto.BrandResponse` (id/name/description)

- [ ] **Run:** `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.BrandV1ApiE2ETest"` → 통과.

- [ ] **Add `.http` 예시:** `http/commerce-api/brand-v1.http` 에 `GET {{commerce-api}}/api/v1/brands/1` 추가.

- [ ] **Commit:** `feat: add GET /api/v1/brands/{brandId}`

---

## Task 4. 브랜드 어드민 API — 목록/상세/등록/수정/삭제

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/admin/brand/BrandAdminV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/admin/brand/BrandAdminV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandFacade.kt` (목록/생성/수정/삭제 메서드 추가)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandInfo.kt` (어드민 응답용 with timestamps 추가)
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/admin/BrandAdminV1ApiE2ETest.kt`
- Create: `http/commerce-api/brand-admin-v1.http`

각 엔드포인트를 한 체크박스(=하나의 동작 + 통과시키는 최소 구현)로 잡는다. 모든 E2E 는 `X-Loopers-Ldap: loopers.admin` 헤더를 포함한다.

체크리스트:

- [ ] **Red(E2E):** `POST /api-admin/v1/brands` — 등록 성공 후 응답에 `id/name/description/createdAt/updatedAt` 포함.

  ```kotlin
  @Test
  fun createsBrand_whenRequestValid() {
      val req = BrandAdminV1Dto.CreateRequest(name = "Nike", description = "Just do it.")
      val type = object : ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandAdminResponse>>() {}
      val response = rest.exchange("/api-admin/v1/brands", HttpMethod.POST, HttpEntity(req, adminHeaders()), type)
      assertAll(
          { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
          { assertThat(response.body?.data?.id).isNotNull() },
          { assertThat(response.body?.data?.createdAt).isNotNull() },
      )
  }
  ```

- [ ] **Green:** `BrandFacade.create(command) -> BrandAdminInfo`, `BrandAdminV1Controller.create(...)`. `BrandInfo` 와 별개로 `BrandAdminInfo`(timestamps 포함)를 application 레이어에 추가하거나, `BrandInfo` 에 `createdAt/updatedAt` nullable 필드 추가 후 어드민 매핑에서만 채우는 방식 중 **전자**(별도 클래스) 선택 — 고객 응답에서 timestamps 가 새어나가지 않도록 보장.

- [ ] **Red(E2E):** `GET /api-admin/v1/brands?page=0&size=20` — 페이지 목록.

  ```kotlin
  @Test
  fun returnsPagedBrands() {
      repeat(3) { brandJpaRepository.save(BrandModel("brand-$it", "")) }
      val type = object : ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandPageResponse>>() {}
      val response = rest.exchange(
          "/api-admin/v1/brands?page=0&size=20", HttpMethod.GET, HttpEntity<Any>(adminHeaders()), type,
      )
      assertThat(response.body?.data?.totalElements).isEqualTo(3)
  }
  ```

- [ ] **Green:** `BrandFacade.list(pageable)`, `BrandPageResponse(content, totalElements, totalPages, page, size)`.

- [ ] **Red(E2E):** `GET /api-admin/v1/brands/{brandId}` — 존재 시 200, 미존재 시 404. (구현은 고객 조회와 동일 도메인 메서드 재사용, 응답 DTO 만 다름.)

- [ ] **Green:** `BrandAdminV1Controller.getById(...)` 추가.

- [ ] **Red(E2E):** `PUT /api-admin/v1/brands/{brandId}` — 이름/설명 변경.

  ```kotlin
  @Test
  fun updatesBrand() {
      val saved = brandJpaRepository.save(BrandModel("old", ""))
      val req = BrandAdminV1Dto.UpdateRequest(name = "new", description = "new desc")
      val response = rest.exchange(
          "/api-admin/v1/brands/${saved.id}", HttpMethod.PUT, HttpEntity(req, adminHeaders()),
          object : ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandAdminResponse>>() {},
      )
      assertThat(response.body?.data?.name).isEqualTo("new")
  }
  ```

- [ ] **Green:** `BrandModel.update(name, description)` 메서드(검증 재사용) + `BrandService.update` + facade/controller.

- [ ] **Red(E2E):** `DELETE /api-admin/v1/brands/{brandId}` — 삭제 후 조회 시 404. (cascade 는 Task 8.)

- [ ] **Green:** `BrandService.delete(id)` + `BrandAdminV1Controller.delete(...)`.

- [ ] **Activate** Task 1 의 `AdminAuthInterceptorE2ETest` 의 `@Disabled` 제거 후 실행 → 통과 확인.

- [ ] **Add `.http`:** 다섯 엔드포인트 모두 `brand-admin-v1.http` 에 예시 추가.

- [ ] **Commit:** `feat: add brand admin endpoints (CRUD + paging)`

---

## Task 5. 상품 도메인 모델 + 영속성 + `ProductSort`

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductSort.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductModelTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt`

체크리스트:

- [ ] **Red(단위):** `ProductModel` 생성 시 음수 가격/음수 재고/빈 이름 거부.

  ```kotlin
  @DisplayName("상품을 생성할 때,")
  @Nested
  inner class Create {
      @DisplayName("가격이 음수면 BAD_REQUEST 가 발생한다.")
      @Test
      fun throwsBadRequest_whenPriceNegative() {
          val exception = assertThrows<CoreException> {
              ProductModel(brandId = 1L, name = "Air Max", description = "", price = -1, stock = 0)
          }
          assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
      }

      @DisplayName("재고가 음수면 BAD_REQUEST 가 발생한다.")
      @Test
      fun throwsBadRequest_whenStockNegative() { /* 유사 */ }

      @DisplayName("이름이 공백이면 BAD_REQUEST 가 발생한다.")
      @Test
      fun throwsBadRequest_whenNameBlank() { /* 유사 */ }
  }
  ```

- [ ] **Green:** `ProductModel` 구현. `brandId: Long` 필드 보유. `update(name, description, price, stock)` 메서드 — **brandId 는 인자에 없음**(요구사항: 브랜드 변경 불가).

- [ ] **Red(단위):** 존재하지 않는 `brandId` 로 상품 등록 시 `BAD_REQUEST`. (도메인 단위 테스트에선 `BrandRepository` 도 In-Memory.)

  ```kotlin
  @Test
  fun throwsBadRequest_whenBrandMissing() {
      val brandRepo = InMemoryBrandRepository()
      val productRepo = InMemoryProductRepository()
      val service = ProductService(productRepo, brandRepo)
      val exception = assertThrows<CoreException> {
          service.create(ProductService.CreateProductCommand(brandId = 999L, name = "n", description = "", price = 1000, stock = 10))
      }
      assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
  }
  ```

- [ ] **Green:** `ProductService.create` 가 `brandRepository.existsById(brandId)` 검증 후 저장.

- [ ] **Red(단위):** `ProductSort` enum + `toPageable(page, size)` 헬퍼.

  ```kotlin
  @Test
  fun sortsByIdDesc_whenLatest() {
      val pageable = ProductSort.LATEST.toPageable(page = 0, size = 20)
      assertThat(pageable.sort.toString()).contains("id: DESC")
  }

  @Test
  fun sortsByPriceAsc_whenPriceAsc() {
      val pageable = ProductSort.PRICE_ASC.toPageable(page = 0, size = 20)
      assertThat(pageable.sort.toString()).contains("price: ASC")
  }
  ```

- [ ] **Green:** `ProductSort` enum.

  ```kotlin
  enum class ProductSort(private val sort: Sort) {
      LATEST(Sort.by(Sort.Direction.DESC, "id")),
      PRICE_ASC(Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.ASC, "id"))),
      ;

      fun toPageable(page: Int, size: Int): Pageable {
          val safeSize = size.coerceIn(1, 100)
          val safePage = page.coerceAtLeast(0)
          return PageRequest.of(safePage, safeSize, sort)
      }

      companion object {
          fun from(raw: String?): ProductSort = when (raw?.lowercase()) {
              "price_asc" -> PRICE_ASC
              else -> LATEST // null/latest/미지원값(likes_desc 등)은 latest 로 폴백
          }
      }
  }
  ```

- [ ] **Red(통합):** `@SpringBootTest` — 상품 저장 후 `findAll(brandId = null, pageable)` / `findAll(brandId = X, pageable)` 분기 검증.

  ```kotlin
  @DisplayName("brandId 필터 없이 페이지 조회하면 모든 상품을 반환한다.")
  @Test
  fun findsAll_whenBrandIdNull() {
      val brandA = brandJpaRepository.save(BrandModel("A", ""))
      val brandB = brandJpaRepository.save(BrandModel("B", ""))
      productJpaRepository.saveAll(listOf(
          ProductModel(brandA.id, "p1", "", 1000, 10),
          ProductModel(brandB.id, "p2", "", 2000, 10),
      ))
      val page = productRepository.findAll(brandId = null, pageable = ProductSort.LATEST.toPageable(0, 20))
      assertThat(page.totalElements).isEqualTo(2)
  }

  @DisplayName("brandId 필터로 조회하면 해당 브랜드 상품만 반환한다.")
  @Test
  fun findsByBrand_whenBrandIdGiven() { /* 유사 */ }
  ```

- [ ] **Green:** `ProductRepository.findAll(brandId: Long?, pageable: Pageable): Page<ProductModel>` + `ProductRepositoryImpl` 분기:

  ```kotlin
  override fun findAll(brandId: Long?, pageable: Pageable): Page<ProductModel> =
      if (brandId == null) jpa.findAll(pageable)
      else jpa.findByBrandId(brandId, pageable)
  ```

  `ProductJpaRepository.findByBrandId(brandId, pageable)` 추가.

- [ ] **Run:** `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.*"` 통과.

- [ ] **Commit:** `feat: add product domain, sort, repository with brand filter`

---

## Task 6. 상품 고객 API — `GET /api/v1/products`, `GET /api/v1/products/{productId}`

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Dto.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ProductV1ApiE2ETest.kt`
- Create: `http/commerce-api/product-v1.http`

체크리스트:

- [ ] **Red(E2E):** `GET /api/v1/products/{productId}` — 존재 시 200 + 상품 정보, 미존재 시 404.

- [ ] **Green:** `ProductFacade.getById(id) -> ProductInfo`, `ProductV1Controller.getProduct`, `ProductV1Dto.ProductResponse(id, brandId, name, description, price, stock)`.

- [ ] **Red(E2E):** `GET /api/v1/products?sort=latest&page=0&size=20` — 페이지 반환, 정렬 검증.

  ```kotlin
  @Test
  fun returnsLatestFirst_whenSortLatest() {
      val brand = brandJpaRepository.save(BrandModel("Nike", ""))
      val older = productJpaRepository.save(ProductModel(brand.id, "old", "", 1000, 10))
      val newer = productJpaRepository.save(ProductModel(brand.id, "new", "", 2000, 10))
      val type = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {}
      val response = rest.exchange("/api/v1/products?sort=latest", HttpMethod.GET, null, type)
      assertThat(response.body?.data?.content?.first()?.id).isEqualTo(newer.id)
  }
  ```

- [ ] **Green:** `ProductFacade.list(brandId: Long?, sort: ProductSort, page, size) -> ProductPageInfo` → controller 가 query param 받아 위임.

- [ ] **Red(E2E):** `GET /api/v1/products?brandId={X}` — 다른 브랜드의 상품은 결과에 포함되지 않는다.

- [ ] **Green:** controller 에서 `@RequestParam(required = false) brandId: Long?` 전달.

- [ ] **Red(E2E):** `GET /api/v1/products?sort=price_asc` — 가격 오름차순 검증. 잘못된 정렬값(`?sort=invalid`)도 200 + latest 폴백.

- [ ] **Green:** controller 에서 `ProductSort.from(sortParam)` 사용. (잘못된 enum 매핑을 ControllerAdvice 에 맡기지 않고 도메인 헬퍼에서 안전 변환.)

- [ ] **Add `.http`:** 3종 쿼리 예시(`?brandId=&sort=latest&page=0&size=20`, `?sort=price_asc`, `/{id}`).

- [ ] **Commit:** `feat: add product list/detail customer API`

---

## Task 7. 상품 어드민 API — 목록/상세/등록/수정/삭제

**Files**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/admin/product/ProductAdminV1Controller.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/admin/product/ProductAdminV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt` (어드민용 CRUD 메서드)
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt` (`ProductAdminInfo` 별도 클래스 추가, timestamps 포함)
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/admin/ProductAdminV1ApiE2ETest.kt`
- Create: `http/commerce-api/product-admin-v1.http`

체크리스트:

- [ ] **Red(E2E):** `POST /api-admin/v1/products` — 등록.

  ```kotlin
  @Test
  fun createsProduct() {
      val brand = brandJpaRepository.save(BrandModel("Nike", ""))
      val req = ProductAdminV1Dto.CreateRequest(brandId = brand.id, name = "Air Max", description = "", price = 159000, stock = 10)
      val response = rest.exchange(
          "/api-admin/v1/products", HttpMethod.POST, HttpEntity(req, adminHeaders()),
          object : ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductAdminResponse>>() {},
      )
      assertThat(response.statusCode.is2xxSuccessful).isTrue()
  }
  ```

- [ ] **Red(E2E):** 존재하지 않는 `brandId` 로 등록 시 400.

  ```kotlin
  @Test
  fun returnsBadRequest_whenBrandMissing() {
      val req = ProductAdminV1Dto.CreateRequest(brandId = 999L, name = "x", description = "", price = 1, stock = 1)
      val response = rest.exchange(
          "/api-admin/v1/products", HttpMethod.POST, HttpEntity(req, adminHeaders()), ApiResponse::class.java,
      )
      assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
  }
  ```

- [ ] **Green:** `ProductFacade.create(command) -> ProductAdminInfo`, controller wiring. `ProductService.create` 가 이미 brand 존재 검증함(Task 5).

- [ ] **Red(E2E):** `GET /api-admin/v1/products?page=0&size=20&brandId={X}` — 페이지 + 옵션 필터. 어드민 응답에는 `createdAt/updatedAt` 포함.

- [ ] **Green:** `ProductFacade.adminList(brandId, pageable) -> Page<ProductAdminInfo>` (정렬은 어드민에선 `id ASC` 또는 `id DESC` 등 고정 — 본 계획은 `LATEST` 기본값).

- [ ] **Red(E2E):** `GET /api-admin/v1/products/{productId}` — 존재 시 200 + 어드민 응답, 미존재 시 404.

- [ ] **Green:** `ProductAdminV1Controller.getById`.

- [ ] **Red(E2E):** `PUT /api-admin/v1/products/{productId}` — 이름/설명/가격/재고 변경. **요청 바디에 `brandId` 가 있어도 무시되어야 한다.** (DTO 에 아예 brandId 필드 없음.)

  ```kotlin
  @Test
  fun updatesProduct_butBrandIdIsImmutable() {
      val brandA = brandJpaRepository.save(BrandModel("A", ""))
      val product = productJpaRepository.save(ProductModel(brandA.id, "old", "", 100, 1))
      val req = ProductAdminV1Dto.UpdateRequest(name = "new", description = "new", price = 200, stock = 2)
      val response = rest.exchange(
          "/api-admin/v1/products/${product.id}", HttpMethod.PUT, HttpEntity(req, adminHeaders()),
          object : ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductAdminResponse>>() {},
      )
      assertAll(
          { assertThat(response.body?.data?.name).isEqualTo("new") },
          { assertThat(response.body?.data?.brandId).isEqualTo(brandA.id) }, // brandA 그대로
      )
  }
  ```

- [ ] **Green:** `ProductModel.update(name, description, price, stock)` + `ProductService.update(id, command)` + facade/controller. `UpdateRequest` 에 `brandId` 필드 없음.

- [ ] **Red(E2E):** `DELETE /api-admin/v1/products/{productId}` — 삭제 후 조회 시 404.

- [ ] **Green:** `ProductService.delete(id)` + controller.

- [ ] **Add `.http`:** 다섯 엔드포인트 예시.

- [ ] **Commit:** `feat: add product admin endpoints`

---

## Task 8. 브랜드 삭제 시 상품 동반 삭제(Cascade)

**Files**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/brand/BrandService.kt` — 의존성에 `ProductRepository` 추가
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt` — `deleteAllByBrandId(brandId)` 추가
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/brand/BrandServiceIntegrationTest.kt` — cascade 테스트 추가
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/admin/BrandAdminV1ApiE2ETest.kt` — cascade E2E 추가

**설계 결정:**
- DB FK ON DELETE CASCADE 에 의존하지 않는다 — 도메인 의도를 코드로 명시(JPA 환경에선 FK 가 항상 같은 명세를 갖지 않을 수 있음).
- `BrandService.delete(id)` 가 **동일 트랜잭션에서** `productRepository.deleteAllByBrandId(id)` 호출 → `brandRepository.delete(brand)`.
- Brand 와 Product 는 별도 Aggregate. Application 단의 cascade 가 아니라 **도메인 서비스(`BrandService`) 안** 에서 처리한다(상품 라이프사이클의 한 시나리오로 본다).

체크리스트:

- [ ] **Red(통합):** 브랜드 삭제 시 해당 브랜드의 상품도 모두 사라진다.

  ```kotlin
  @Test
  fun deletesBrandAndItsProducts() {
      val brand = brandJpaRepository.save(BrandModel("Nike", ""))
      productJpaRepository.saveAll(listOf(
          ProductModel(brand.id, "p1", "", 1000, 10),
          ProductModel(brand.id, "p2", "", 2000, 10),
      ))
      brandService.delete(brand.id)
      assertAll(
          { assertThat(brandJpaRepository.findById(brand.id)).isEmpty },
          { assertThat(productJpaRepository.findByBrandId(brand.id, PageRequest.of(0, 10)).totalElements).isZero },
      )
  }
  ```

- [ ] **Green:** `ProductRepository.deleteAllByBrandId(brandId)` + JpaRepository `@Modifying @Query("delete from ProductModel p where p.brandId = :brandId") fun deleteAllByBrandId(brandId: Long)`. `BrandService.delete` 에서 호출 후 `brandRepository.delete(brand)`.

  ```kotlin
  @Transactional
  fun delete(id: Long) {
      val brand = brandRepository.findById(id)
          ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
      productRepository.deleteAllByBrandId(id)
      brandRepository.delete(brand)
  }
  ```

- [ ] **Red(E2E):** 어드민 `DELETE /api-admin/v1/brands/{brandId}` 후, 같은 브랜드의 상품을 `GET /api/v1/products/{productId}` 로 조회 → 404.

- [ ] **Green:** 위 도메인 변경으로 자동 통과 확인.

- [ ] **Run all:** `./gradlew :apps:commerce-api:test` 전체 통과.

- [ ] **Commit:** `feat: delete brand cascades to its products`

---

## Task 9. 마무리 — 전체 검증 + 문서

체크리스트:

- [ ] **빌드/린트/테스트:**
  - `./gradlew ktlintCheck`
  - `./gradlew :apps:commerce-api:test`
  - `./gradlew :apps:commerce-api:build` (테스트 포함 전체)
  - 모든 명령이 0 으로 종료되어야 함.

- [ ] **수동 검증(`local` 프로필):**
  - `docker-compose -f ./docker/infra-compose.yml up -d`
  - `./gradlew :apps:commerce-api:bootRun`
  - IntelliJ `.http` 파일에서 9 개 엔드포인트(브랜드 6 + 상품 5 중 고객/어드민 분리) 차례로 실행, 응답 메타데이터/데이터 확인.

- [ ] **`plan.md` 갱신:** Round 2 체크박스 항목들을 ✅ 로 마킹하거나, 본 문서로 링크 추가.

- [ ] **Final commit:** `chore: round 2 (brands & products) complete`

---

## Self-Review (작성자 점검)

**1. 스펙 커버리지** — 요구사항 표 11 개 엔드포인트(브랜드 고객 1, 브랜드 어드민 5, 상품 고객 2, 상품 어드민 5) 매핑:
- `GET /api/v1/brands/{brandId}` → Task 3 ✓
- `GET /api-admin/v1/brands?page=&size=` → Task 4 ✓
- `GET /api-admin/v1/brands/{brandId}` → Task 4 ✓
- `POST /api-admin/v1/brands` → Task 4 ✓
- `PUT /api-admin/v1/brands/{brandId}` → Task 4 ✓
- `DELETE /api-admin/v1/brands/{brandId}` (+ 상품 cascade) → Task 4 + Task 8 ✓
- `GET /api/v1/products?brandId=&sort=&page=&size=` → Task 6 ✓
- `GET /api/v1/products/{productId}` → Task 6 ✓
- `GET /api-admin/v1/products?page=&size=&brandId=` → Task 7 ✓
- `GET /api-admin/v1/products/{productId}` → Task 7 ✓
- `POST /api-admin/v1/products` (브랜드 존재 검증) → Task 7 ✓
- `PUT /api-admin/v1/products/{productId}` (brandId 불변) → Task 7 ✓
- `DELETE /api-admin/v1/products/{productId}` → Task 7 ✓
- 어드민 헤더 인증 → Task 1 + Task 4 ✓
- 고객/어드민 응답 분리(timestamps 노출 제어) → Task 4/Task 7 ✓

**2. 의도적 제외(범위 외)**
- `likes_desc` 정렬: Round 3 (Likes)
- 재고 차감/주문 스냅샷: Round 4 (Orders)
- 동시성/멱등성: 향후 Round
- 회원/어드민 진짜 인증·인가: 본 과제 스코프 외(요구사항 명시)

**3. 타입 일관성 점검**
- `BrandModel(name, description)` — Task 2/3/4 동일 시그니처.
- `ProductModel(brandId, name, description, price, stock)` — Task 5/6/7 동일. `update` 메서드는 `brandId` 인자 없음.
- `BrandRepository` / `ProductRepository` 시그니처는 Task 2/5/8 에서 정의된 그대로 사용.
- `BrandInfo` (고객) vs `BrandAdminInfo` (어드민, timestamps) 분리 — Task 3 vs Task 4.
- `ProductInfo` vs `ProductAdminInfo` 분리 — Task 6 vs Task 7.

**4. Placeholder 점검** — TBD/TODO/“적절한 검증 추가” 류 표현 없음.

---

## Execution Handoff

이 계획은 `docs/plans/2026-05-19-brands-and-products.md` 에 저장되었습니다.

두 가지 실행 방식 중 선택해주세요.

1. **Subagent-Driven (권장)** — Task 단위로 별도 서브에이전트를 디스패치하고, 두 단계 리뷰(중간 + 최종)로 진행. 빠른 반복.
2. **Inline Execution** — 본 세션 내에서 `executing-plans` 스킬로 Task 들을 체크포인트 단위 배치 실행.

다음 라운드 계획(Round 3: Likes, Round 4: Orders)은 본 라운드 완료 후 요청 시 작성합니다.

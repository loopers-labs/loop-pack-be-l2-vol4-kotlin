# 요구사항.md

---

## 📐 프로젝트 구조 및 개발 패턴
```
[ HTTP 요청 ]
      │
      ▼
┌─────────────────────────────────┐
│  interfaces/api/                │  ← 클라이언트와의 경계
│  XxxV1Controller                │    요청 받고 응답 내보내는 역할
│  (implements XxxV1ApiSpec)      │    Dto로 변환
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  application/                   │  ← Facade 패턴
│  XxxFacade                      │    여러 Service를 조합하는 유즈케이스 조율
│                                 │    ex) 회원가입 시 유저 저장 + 포인트 지급 + 이메일 발송
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  domain/                        │  ← 핵심 비즈니스 로직
│  XxxService                     │    단일 도메인 책임만 가짐
│  XxxRepository (interface)      │
│  XxxModel (Entity)              │
└─────────────┬───────────────────┘
              │  (interface 호출)
              ▼
┌─────────────────────────────────┐
│  infrastructure/                │  ← DB 접근 구현체
│  XxxRepositoryImpl              │    domain의 Repository 인터페이스 구현
│  XxxJpaRepository               │    실제 SQL 날리는 곳
└─────────────────────────────────┘
```

> **Facade 분리 이유**: `XxxService`는 단일 도메인 책임만 가지고, 여러 Service를 엮는 유즈케이스 흐름은 `XxxFacade`가 담당한다.

| 레이어 | 위치 | 역할 |
|--------|------|------|
| `interfaces/api/` | `XxxV1Controller`, `XxxV1ApiSpec`, `XxxV1Dto` | HTTP 요청/응답 처리 |
| `application/` | `XxxFacade`, `XxxInfo` | 유즈케이스 조율 (Service 조합) |
| `domain/` | `XxxModel`(Entity), `XxxService`, `XxxRepository`(interface) | 비즈니스 로직 |
| `infrastructure/` | `XxxJpaRepository`, `XxxRepositoryImpl` | DB 접근 구현체 |
| `support/` | `CoreException`, `ErrorType` | 공통 예외 처리 |

---

### 각 레이어 구현 규칙

#### Controller (`XxxV1Controller`)
- `XxxV1ApiSpec` 인터페이스를 구현
- Swagger 어노테이션은 **ApiSpec에만** 작성
- 응답은 항상 `ApiResponse<T>`로 래핑
- Facade를 호출하고 결과를 Dto로 변환해서 반환

#### Dto (`XxxV1Dto`)
- 하나의 클래스 안에 Request/Response를 inner `data class`로 정의
- `from(info: XxxInfo)` companion 팩토리 메서드로 변환

#### Facade (`XxxFacade`)
- `@Component`
- 여러 Service 조합이 필요한 유즈케이스 조율
- Service 호출 결과를 `XxxInfo`로 변환해 반환

#### Info (`XxxInfo`)
- `data class`
- application 레이어의 응답 모델
- `from(model: XxxModel)` companion 팩토리 메서드로 변환

#### Service (`XxxService`)
- `@Component` + `@Transactional`
- 비즈니스 로직 처리
- 예외는 `CoreException(ErrorType.XXX, "메시지")`로 통일

#### Repository 인터페이스 (`XxxRepository`)
- domain 레이어에 위치 (infrastructure 의존 없음)
- 순수 인터페이스만 정의

```kotlin
interface ExampleRepository {
    fun find(id: Long): ExampleModel?
}
```

#### RepositoryImpl (`XxxRepositoryImpl`)
- `@Component`
- `XxxRepository` 인터페이스 구현
- `XxxJpaRepository`를 주입받아 사용
- Model ↔ Domain 변환 담당

#### JpaRepository (`XxxJpaRepository`)
- `JpaRepository<XxxModel, Long>` 상속
- infrastructure 레이어에 위치

#### Entity (`XxxModel`)
- `BaseEntity` 상속 (id 자동 생성, createdAt/updatedAt/deletedAt 자동 관리)
- `@Entity`, `@Table(name = "테이블명")`
- 유효성 검사는 `init` 블록에서 처리
- 필드는 `protected set`으로 불변성 보호
- `toDomain()` / `companion object { fun from(domain) }` 변환 메서드 보유


### 테스트 구조

| 종류 | 파일명 패턴 | 어노테이션 | 용도 |
|------|------------|-----------|------|
| 단위 테스트 | `XxxModelTest`, `XxxTest` | 없음 (순수 JUnit5) | 도메인 객체 유효성 검사 |
| 서비스 단위 테스트 | `XxxServiceTest` | 없음 (Mockito mock) | Service 비즈니스 로직 |
| 통합 테스트 | `XxxServiceIntegrationTest` | `@SpringBootTest` | Service + 실제 DB (Testcontainers) |
| E2E 테스트 | `XxxV1ApiE2ETest` | `@SpringBootTest(RANDOM_PORT)` | Controller → DB 전체 흐름 |

**공통 규칙**
- `@Nested` inner class로 시나리오 그룹화
- `// arrange / act / assert` 주석으로 단계 구분
- `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`로 테스트 격리 (통합/E2E)



### ✅ To-Do List

**회원가입**
- [x] 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 유효성 검사
- [x] 이미 가입된 로그인 ID로는 가입 불가
- [x] 비밀번호 암호화 저장
- [x] 단위 테스트
- [x] 통합 테스트
- [x] E2E 테스트

**내 정보 조회**
- [x] 이름 마지막 글자 마스킹 처리
- [x] 헤더(X-Loopers-LoginId, X-Loopers-LoginPw)로 유저 인증
- [X] loginId 유효성 체크
- [X] 이름(마스킹) 기능 추가
- [x] 단위 테스트
- [X] 통합 테스트
- [x] E2E 테스트

**비밀번호 수정**
- [x] 헤더(X-Loopers-LoginId, X-Loopers-LoginPw)로 유저 인증
- [x] 기존 비밀번호 일치 여부 확인
- [x] 현재 비밀번호와 동일한 비밀번호로 변경 불가
- [x] 새 비밀번호 암호화 저장
- [x] 단위 테스트
- [x] 통합 테스트
- [x] E2E 테스트

---

### 🗒️ 기능 구현

**대전제**
SOLID 원칙을 따르면서 DDD 관점으로 개발을 한다.
위에서 정리된 "프로젝트 구조 및 개발 패턴"을 참고하여 같은 구조로 개발한다.

**회원가입**

- **필요 정보 : { 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 }**
- 이미 가입된 로그인 ID 로는 가입이 불가능함
- 각 정보는 포맷에 맞는 검증 필요 (이름, 이메일, 생년월일)
- 비밀번호는 암호화해 저장하며, 아래와 같은 규칙을 따름

    ```markdown
    1. 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.
    2. 생년월일은 비밀번호 내에 포함될 수 없습니다.
    ```


> 이후, 유저 정보가 필요한 모든 요청은 아래 헤더를 통해 요청
* **`X-Loopers-LoginId`** : 로그인 ID
* **`X-Loopers-LoginPw`** : 비밀번호
>

**내 정보 조회**

- **반환 정보 : { 로그인 ID, 이름, 생년월일, 이메일 }**
- 로그인 ID 는 영문과 숫자만 허용
- 이름은 마지막 글자를 마스킹해 반환

> 마스킹 문자는 `*` 로 통일
>

**비밀번호 수정**

- **필요 정보 : { 기존 비밀번호, 새 비밀번호 }**
- 비밀 번호 RULE 을 따르되, 현재 비밀번호는 사용할 수 없습니다.

> **비밀번호 RULE**
* 영문 대/소문자, 숫자, 특수문자 사용 가능
* 생년월일 사용 불가
>
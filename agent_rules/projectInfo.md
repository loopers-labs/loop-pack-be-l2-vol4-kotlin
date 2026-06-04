# loopers-kotlin-spring-template

Spring Boot + Kotlin 기반의 Loopers 커머스 멀티 모듈 프로젝트.

이 문서는 프로젝트의 현재 사실과 요구사항을 기록한다.
개발 방식, DDD 경계, 테스트 작성 규칙은 `agent_rules/groundRules.md` 를 따른다.

## 기술 스택

| 분류 | 항목 | 버전 |
| --- | --- | --- |
| 언어 | Kotlin (JVM) | 2.0.20 |
| 런타임 | Java Toolchain | 21 |
| 빌드 | Gradle (Kotlin DSL) | 8.14.4 (wrapper) |
| 프레임워크 | Spring Boot | 3.4.4 |
| 의존성 관리 | io.spring.dependency-management | 1.1.7 |
| ORM | Spring Data JPA (Hibernate) | Spring Boot 관리 |
| DB | MySQL | mysql-connector-j / Testcontainers |
| API 문서 | springdoc-openapi (webmvc-ui) | 2.8.16 |
| 직렬화 | jackson-module-kotlin | Spring Boot 관리 |
| Validation | spring-boot-starter-validation | Spring Boot 관리 |
| 테스트 | spring-boot-starter-test, kotlin-test-junit5, MockK, JUnit Platform Launcher | Spring Boot 관리 / MockK 1.13.13 |

### Kotlin 컴파일러/플러그인 설정
- `kotlin("plugin.spring")` — Spring 컴포넌트 클래스 자동 `open`
- `kotlin("plugin.jpa")` — JPA 엔티티 no-arg 생성자
- `allOpen` 대상 어노테이션: `jakarta.persistence.Entity`, `MappedSuperclass`, `Embeddable`
- `freeCompilerArgs`: `-Xjsr305=strict`
- 테스트 러너: `useJUnitPlatform()`

## 프로젝트 메타
- `group`: `com.loopers`
- `version`: Git short hash 기반
- `rootProject.name`: `loopers-kotlin-spring-template`
- 기본 패키지: `com.loopers`

## 모듈 구조

멀티 모듈 구조.
```
loopers-kotlin-spring-template/
├── apps/
│   ├── commerce-api/       # 사용자 API, 도메인, 애플리케이션, 인프라 구현
│   ├── commerce-batch/     # 배치 애플리케이션
│   └── commerce-streamer/  # 스트리밍 애플리케이션
├── modules/
│   ├── jpa/                # JPA/DataSource 설정과 테스트 컨테이너
│   ├── redis/              # Redis 설정과 테스트 컨테이너
│   └── kafka/              # Kafka 설정
└── supports/
    ├── jackson/
    ├── logging/
    └── monitoring/
```

### commerce-api 사용자 기능 패키지 구조

사용자 기능은 기능 단위 응집을 우선해 `com.loopers.domain.user` 아래에 둔다.

```
domain/user/
├── application/            # 유스케이스, command/info DTO, facade
├── infrastructure/
│   └── persistence/         # JPA Entity, Spring Data Repository, port 구현체
├── presentation/            # Controller, API spec, request/response DTO
│   └── auth/                # 사용자 인증 헤더, LoginUser resolver/config
├── model/                   # UserModel 등 순수 도메인 모델
├── vo/                      # LoginId, Password, Name, Birthday, Email
├── port/                    # UserRepository, PasswordEncoder
└── exception/               # 사용자 도메인/경계 예외
```

- 사용자 전용 `HandlerMethodArgumentResolver`, 인증 어노테이션, 요청 헤더 상수, WebMvc 설정은 `domain/user/presentation/auth` 에 둔다.
- `UserModel` 은 VO 기반 POJO 로 유지한다.
- JPA Entity 는 primitive column 매핑을 유지하고, infrastructure 경계에서 VO 와 변환한다.

## 자주 쓰는 명령

```bash
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-api:test
./gradlew ktlintCheck
```

## 참고
- Swagger UI: `springdoc-openapi-starter-webmvc-ui` 의존성으로 기본 경로 `/swagger-ui.html` 사용 가능.
- `test` 프로필은 MySQL Testcontainers 기반으로 동작한다.

## 설계/테스트 규칙 위치

- DDD 아키텍처 규칙, JPA Entity 와 POJO 도메인 객체 분리 원칙은 `agent_rules/groundRules.md` 의 "아키텍처 규칙 (DDD)" 절을 따른다.
- 테스트 종류, 파일명 접미사, 디렉토리 정책, 픽스처(`*TestSteps`) 위치, 테스트 명명 규약, 모킹 정책, 의미 없는 테스트 회피 원칙은 `agent_rules/groundRules.md` 의 "테스트 규약" 절을 따른다.
- PR 코드리뷰 기반 작업계획은 `agent_rules/groundRules.md` 의 "PR 코드리뷰 대응 계획" 절을 따른다.

## 인증 헤더 규약

유저 정보가 필요한 모든 요청은 아래 두 헤더로 식별/인증한다.

| 헤더 | 의미 |
| --- | --- |
| `X-Loopers-LoginId` | 로그인 아이디 |
| `X-Loopers-LoginPw` | 로그인 비밀번호 (평문, 서버에서 인코딩 후 매칭) |

- **회원가입 (`POST /api/users`)**: 가입 시점에는 계정이 없으므로 **request body** 로 `loginId`, `password`, `name`, `birthday`, `email` 전달. 위 헤더는 사용하지 않는다.
- **그 외 사용자 정보 필요 API**: 반드시 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 인증한다. 누락/불일치 시 `401`.
- 비밀번호는 저장 시 BCrypt 로 단방향 인코딩되며, 매칭은 `PasswordEncoder.matches(raw, encoded)` 로 수행한다.

## 사용자 정보 조회 API

### `GET /users/me`

로그인한 사용자의 정보를 반환한다.

#### Request

| 위치 | 이름 | 필수 | 의미 |
| --- | --- | --- | --- |
| Header | `X-Loopers-LoginId` | Y | 로그인 아이디 |
| Header | `X-Loopers-LoginPw` | Y | 평문 비밀번호 |

- 조회 API이므로 request body 는 사용하지 않는다.
- request DTO 는 body 가 있는 API에서 생성한다.

#### Response `200 OK`

```json
{
  "loginId": "user01",
  "name": "홍길*",
  "birthday": "1996-01-01",
  "email": "user@example.com"
}
```

- `loginId`: 영문/숫자 4~20자.
- `name`: 마지막 글자를 `*`로 마스킹한다. 1글자 이름은 `*`로 반환한다.
- `birthday`: ISO-8601 날짜 문자열(`yyyy-MM-dd`).
- `email`: 도메인 Email VO와 같은 간이 RFC 5322 형식.

#### Error

| 조건 | 상태 |
| --- | --- |
| 로그인ID 헤더 누락 | `401 Unauthorized` |
| 비밀번호 헤더 누락 | `401 Unauthorized` |
| 사용자 없음 | `401 Unauthorized` |
| 비밀번호 불일치 | `401 Unauthorized` |

API 예외 응답 상태는 Spring 표준 `HttpStatus` 로 표현한다.

## 비밀번호 변경 API

### `PATCH /api/users/me/password`

로그인한 사용자의 비밀번호를 변경한다.

#### Request

| 위치 | 이름 | 필수 | 의미 |
| --- | --- | --- | --- |
| Header | `X-Loopers-LoginId` | Y | 로그인 아이디 |
| Header | `X-Loopers-LoginPw` | Y | 현재 평문 비밀번호 |
| Body | `newPassword` | Y | 새 평문 비밀번호 |

```json
{
  "newPassword": "NewPass1!"
}
```

- 현재 비밀번호 검증은 기존 인증 헤더를 사용한다.
- 서비스 트랜잭션 안에서 회원 PK 기준으로 잠금 조회한 뒤 최신 비밀번호와 현재 비밀번호를 다시 검증한다.
- `newPassword`: 회원가입과 같은 비밀번호 규칙을 따른다.
- 현재 비밀번호와 같은 새 비밀번호는 사용할 수 없다.

#### Response `200 OK`

성공 응답만 반환한다.

#### Error

| 조건 | 상태 |
| --- | --- |
| 로그인ID 헤더 누락 | `401 Unauthorized` |
| 비밀번호 헤더 누락 | `401 Unauthorized` |
| 사용자 없음 | `401 Unauthorized` |
| 현재 비밀번호 불일치 | `401 Unauthorized` |
| 새 비밀번호 포맷 오류 | `400 Bad Request` |
| 새 비밀번호에 생년월일 포함 | `400 Bad Request` |
| 새 비밀번호가 현재 비밀번호와 같음 | `400 Bad Request` |

## 회원가입 API

### `POST /api/users`

신규 사용자를 가입시킨다.

#### Request

```json
{
  "loginId": "user01",
  "password": "Abcd123!",
  "name": "홍길동",
  "birthday": "1996-01-01",
  "email": "user@example.com"
}
```

- request DTO 는 `domain/user/presentation/request/SignUpRequest` 를 사용한다.
- `loginId`: 영문/숫자 4~20자, 이미 가입된 값은 사용할 수 없다.
- `password`: 8~16자, 영문 대문자/소문자/숫자/특수문자 각 1개 이상, 생년월일 토큰(`yyyyMMdd`/`yyMMdd`/`MMdd`) 포함 불가.
- `name`: 공백이 아닌 1~50자.
- `birthday`: ISO-8601 날짜 문자열(`yyyy-MM-dd`), 과거 날짜만 허용.
- `email`: 도메인 Email VO와 같은 간이 RFC 5322 형식.

#### Response `201 Created`

가입된 회원 정보를 반환한다.

```json
{
  "id": 1,
  "loginId": "user01",
  "name": "홍길동",
  "birthday": "1996-01-01",
  "email": "user@example.com"
}
```

#### Error

| 조건 | 상태 |
| --- | --- |
| 요청 필드 포맷 오류 | `400 Bad Request` |
| 비밀번호에 생년월일 포함 | `400 Bad Request` |
| 이미 가입된 로그인ID | `409 Conflict` |

로그인ID 유일성은 DB unique constraint 가 최종 보장한다. 서비스는 사전 중복 검사를 수행하되,
실제 저장 시점에 다른 요청이 같은 로그인ID를 먼저 선점하면 constraint 예외를 `409 Conflict` 비즈니스 에러로 변환한다.

## 도메인 규칙 — 사용자 (User)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| 로그인ID | `LoginId` | 영문/숫자 4~20자, 시스템 전체에서 **Unique** |
| 비밀번호 | `Password` | 평문 8~16자, 영문 대문자/소문자/숫자/특수문자 각 1개 이상, **생년월일 포함 불가** (yyyyMMdd/yyMMdd/MMdd) — 저장은 BCrypt 인코딩 |
| 이름 | `Name` | 공백 제외 1~50자 |
| 생년월일 | `Birthday` | `LocalDate`, 과거 날짜만 허용 |
| 이메일 | `Email` | RFC 5322 간이 형식 |

- 도메인 루트 패키지: `com.loopers.domain.user`
- 애그리거트 루트: `domain/user/model/UserModel`
- `UserModel` 의 사용자 속성은 `LoginId`, `Password`, `Name`, `Birthday`, `Email` VO 로 표현한다.
- 비밀번호: `Password` VO 가 평문 규칙 검증과 인코딩 생성 책임을 보유하며, 생성 후에는 인코딩된 값만 보관.
- 암호화 포트: `domain/user/port/PasswordEncoder` ↔ BCrypt 어댑터(인프라)
- 회원가입 진입점: `domain/user/application/UserService.signUp(command)`
- 사용자 유스케이스 command 는 `domain/user/application/command` 아래에 1클래스 1파일로 둔다.
- `UserInfo`, `UserFacade`, `UserService` 는 사용자 application 계층에 둔다.
- `UserRepository` 는 `domain/user/port` 에 두고, JPA 구현체는 `domain/user/infrastructure/persistence` 에 둔다.
- 중복 로그인ID: `UserService` 가 사전 조회로 검증하고, 저장 시점 DB Unique constraint 충돌은
  `UserRepositoryImpl` 이 `DuplicateLoginIdException` 경계 예외로 변환한 뒤 `UserService` 가 409 비즈니스 에러로 변환한다.
- 도메인 모델과 JPA Entity 는 분리한다. `UserModel`/VO 는 POJO 로 유지하고,
  infrastructure 의 JPA Entity 가 테이블 매핑과 도메인 변환을 담당한다.

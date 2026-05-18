# Account API Architecture

## Decisions

- 회원 기능은 `apps:account-api` 실행 애플리케이션에서 제공한다.
- Account 구현은 `account-domain`, `account-application`, `account-persistence`, `account-security` 모듈로 분리한다.
- 엔티티와 도메인 모델은 현재 코드베이스 관례에 맞춰 하나로 사용한다.
- JPA 공통 기반은 `modules:persistence-core`에 두고, `modules:jpa`와 account domain이 함께 사용한다.
- Spring Security 기반 인증 구현은 `account-security`에 두고, `account-api`는 API controller와 DTO를 담당한다.
- 외부 라우트는 평가 스펙에 맞춰 `/api/v1/users` 컨벤션을 따른다. 내부 도메인 명칭은 `account` 유지 — `user`는 MySQL `mysql.user` / Spring Security `User`와 충돌하므로 내부 코드/스키마에는 도입하지 않는다.

## API Contract

| Method | Path | 인증 | 응답 |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | 불필요 | 빈 data (`ApiResponse` 래핑) |
| GET | `/api/v1/users/me` | 필요 | `loginId`, `name`(마스킹), `birthDate`, `email` |
| PUT | `/api/v1/users/password` | 필요 | 빈 data |

### 인증 헤더 명세 (평가 스펙)

```
X-Loopers-LoginId : 로그인 ID
X-Loopers-LoginPw : 비밀번호
```

- 인증이 필요한 엔드포인트는 두 헤더가 모두 있어야 하며, `AccountHeaderAuthenticationFilter`가 매 요청마다 `accountService.authenticate(loginId, password)`로 검증한다.
- 검증 성공 시 `accountId`, `loginId`를 request attribute에 채워 controller가 `@RequestAttribute`로 받는다.
- 검증 실패는 `AccountAuthenticationEntryPoint`가 공통 `ApiResponse` 실패 형식으로 변환한다. (`CommonErrorCode.UNAUTHORIZED`)
- 스펙상 "인증/인가는 주요 스코프가 아니므로 구현하지 않습니다" 라는 문구는 평가 포인트가 아니라는 의미로 해석한다. "유저는 타 유저의 정보에 직접 접근할 수 없습니다"라는 제약을 보장하려면 최소한의 비밀번호 검증이 필요하므로 현재 구조를 유지한다.
- 매 요청 BCrypt 검증의 성능 부담은 추후 인증 결과 캐싱으로 완화한다 (이번 범위 아님).

### 비밀번호 변경 메서드 결정

- 평가 스펙에 따라 `PUT /api/v1/users/password` 를 사용한다.
- 요청 body는 `currentPassword`, `newPassword`. PUT 시맨틱(전체 교체)과 약간 어긋나지만, 세션 탈취 시 비밀번호가 바뀌는 사고를 막기 위해 `currentPassword` 확인을 유지한다. 인증 도메인에서는 시맨틱 엄격성보다 보안이 우선이다.

### 어드민 영역

- 어드민 헤더 (`X-Loopers-Ldap: loopers.admin`) 는 user 도메인 범위 밖이다.
- 어드민이 사용할 엔드포인트가 구체화되는 시점 (week-02 도메인 설계) 에 함께 정리한다.

## Module Structure

```text
apps/account-api
├── account/api                  # Controller, request DTO, HTTP mapping
└── AccountApiApplication

modules/account-application
└── account/application          # use case, transaction boundary, command

modules/account-domain
└── account/domain               # Account, AccountCredential, VO, validator, PasswordEncryptor port

modules/account-persistence
└── account/persistence          # repository interfaces, Spring Data JPA repositories, adapter config

modules/account-security
└── account/security             # SecurityFilterChain, header filter, entry point, BCrypt PasswordEncryptor

modules/persistence-core         # BaseEntity, minimal Jakarta Persistence API
supports/error                   # ErrorCode, CoreException, status-specific exceptions
supports/web                     # ApiResponse, ControllerAdvice, ResponseBodyAdvice
```

의존 방향은 아래처럼 유지한다.

```text
apps/account-api -> account-application
apps/account-api -> account-persistence
apps/account-api -> account-security
apps/account-api -> supports:web

account-application -> account-domain
account-application -> account-persistence
account-persistence -> account-domain
account-persistence -> modules:jpa
account-security -> account-application
account-security -> account-domain
account-security -> supports:web
account-security -> supports:error

account-domain -> persistence-core
account-domain -> supports:error
modules:jpa -> persistence-core
supports:web -> supports:error
```

`account-domain`은 HTTP DTO, `ApiResponse`, Spring MVC, Spring Security filter, repository interface/구현체를 알지 않는다. 엔티티와 도메인 모델을 함께 쓰므로 JPA annotation은 허용하되, datasource/JPA 설정은 `account-persistence`와 `modules:jpa`가 담당한다.

## Domain Model

회원 프로필과 인증 수단은 분리한다. `Account`는 사용자 프로필이고, `AccountCredential`은 로그인 ID/비밀번호 같은 인증 수단이다. 민감 정보와 인증 방식은 변경 축이 다르고, 한 account가 여러 credential을 가질 수 있으므로 같은 엔티티에 묶지 않는다.

```text
account
├── id
├── name
├── birth_date
├── email
└── base columns

account_credential
├── id
├── account_id
├── method
├── identifier
├── secret
└── base columns
```

초기 모델링 규칙:

- `method = PASSWORD`일 때 `identifier`는 사용자가 입력한 로그인 ID이다.
- `secret`에는 암호화된 비밀번호만 저장한다.
- `account`는 비밀번호를 알지 않는다.
- `account.email`은 가입 계정 단위로 유니크하게 관리한다.
- 유니크 제약은 `account_credential(method, identifier)` 기준으로 둔다.
- 회원가입은 `Account`와 `AccountCredential`을 함께 생성한다.
- 비밀번호 수정은 account profile 변경이 아니라 credential 변경이다.

OAuth 등이 실제로 필요해지면 `method`에 `GOOGLE`, `KAKAO` 같은 값을 추가하고, `identifier`에는 provider subject를 저장한다. 지금은 `PASSWORD`만 구현한다.

## Spring Security

`account-security`는 Spring Security 기반 HTTP 인증 설정과 header 인증 filter, 실패 응답 entry point, `PasswordEncryptor` 구현을 담당한다. `account-api`는 인증 결과를 request attribute로 받아 controller use case에 전달한다.

의존성은 `spring-boot-starter-security`를 유지한다. 현재 `account-security`가 `SecurityFilterChain`, custom filter, entry point, `PasswordEncoder`를 함께 담당하므로 Spring Security 기능 묶음으로 관리하는 편이 단순하다. `spring-security-web/config/crypto` 직접 의존이나 `account-crypto` 분리는 암호화 기능만 별도 재사용해야 하는 요구가 생길 때 검토한다.

filter와 entry point는 servlet API 타입을 직접 참조하므로 `jakarta.servlet-api`를 `compileOnly`로 둔다. 실행 시에는 `account-api`의 web runtime이 servlet API를 제공한다.

기본 전략:

- 회원가입 API는 `permitAll`
- 내 정보 조회, 비밀번호 수정은 인증 필요
- `X-Loopers-LoginId`, `X-Loopers-LoginPw`를 읽는 custom filter 사용
- stateless API로 구성하고 form login/default basic login은 비활성화
- 비밀번호 저장/검증은 Spring Security `PasswordEncoder` 사용
- domain에는 `PasswordEncryptor` port만 둔다
- 인증 성공 시 `AccountPrincipal`을 `SecurityContext`에 저장하고, controller 입력용으로 `accountId`, `loginId` request attribute를 함께 저장한다
- 인증 실패는 `AccountAuthenticationEntryPoint`가 `supports:web`의 공통 `ApiResponse` 형식으로 변환한다
- 인증 실패 코드는 `CommonErrorCode.UNAUTHORIZED`를 사용한다

## Test Responsibility

- `account-domain`: VO, entity behavior, password policy 같은 순수 도메인 규칙
- `account-application`: persistence repository interface와 encryptor를 mock한 use case 흐름
- `account-persistence`: repository interface/adapter, `@DataJpaTest`와 H2 embedded DB 기반 Spring Data JPA 동작
- `account-security`: header authentication filter, entry point, password encoder adapter
- `account-api`: controller mapping, request binding, response wrapping 포함 API wiring
- `supports:error`: 공통 예외와 ErrorCode 동작
- `supports:web`: response wrapping, controller advice, filter writer 동작

## References

- [우아한형제들: 멀티모듈 설계 이야기](https://techblog.woowahan.com/2637/)
- [Spring Boot: Spring Security](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
- [Spring Security: Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security: Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

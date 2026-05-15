# Account API Architecture

## Decisions

- 회원 기능은 `apps:account-api` 실행 애플리케이션에서 제공한다.
- Account 구현은 `account-domain`, `account-application`, `account-persistence`, `account-security` 모듈로 분리한다.
- 엔티티와 도메인 모델은 현재 코드베이스 관례에 맞춰 하나로 사용한다.
- JPA 공통 기반은 `modules:persistence-core`에 두고, `modules:jpa`와 account domain이 함께 사용한다.
- Spring Security 암호화 구현은 `account-security`에 두고, HTTP 인증 설정은 `account-api`에서 조립한다.

## Module Structure

```text
apps/account-api
├── account/api                  # Controller, request DTO, HTTP mapping
└── AccountApiApplication

modules/account-application
└── account/application          # use case, transaction boundary, command

modules/account-domain
└── account/domain               # Account, AccountCredential, VO, validator, ports

modules/account-persistence
└── account/persistence          # Spring Data JPA repositories, adapter config

modules/account-security
└── account/security             # BCrypt PasswordEncryptor implementation

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
account-persistence -> account-domain
account-persistence -> modules:jpa
account-security -> account-domain

account-domain -> persistence-core
account-domain -> supports:error
modules:jpa -> persistence-core
supports:web -> supports:error
```

`account-domain`은 HTTP DTO, `ApiResponse`, Spring MVC, Spring Security filter, Spring Data repository 구현체를 알지 않는다. 엔티티와 도메인 모델을 함께 쓰므로 JPA annotation은 허용하되, datasource/JPA 설정은 `account-persistence`와 `modules:jpa`가 담당한다.

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
- 유니크 제약은 `account_credential(method, identifier)` 기준으로 둔다.
- 회원가입은 `Account`와 `AccountCredential`을 함께 생성한다.
- 비밀번호 수정은 account profile 변경이 아니라 credential 변경이다.

OAuth 등이 실제로 필요해지면 `method`에 `GOOGLE`, `KAKAO` 같은 값을 추가하고, `identifier`에는 provider subject를 저장한다. 지금은 `PASSWORD`만 구현한다.

## Spring Security

`account-security`는 Spring Security crypto 의존성으로 `PasswordEncryptor`를 구현한다. `account-api`는 HTTP 보안 설정과 header 인증 filter를 조립한다.

기본 전략:

- 회원가입 API는 `permitAll`
- 내 정보 조회, 비밀번호 수정은 인증 필요
- `X-Loopers-LoginId`, `X-Loopers-LoginPw`를 읽는 custom filter 사용
- stateless API로 구성하고 form login/default basic login은 비활성화
- 비밀번호 저장/검증은 Spring Security `PasswordEncoder` 사용
- domain에는 `PasswordEncryptor` port만 둔다.

## Test Responsibility

- `account-domain`: VO, entity behavior, password policy 같은 순수 도메인 규칙
- `account-application`: repository port와 encryptor를 mock한 use case 흐름
- `account-persistence`: `@DataJpaTest`와 H2 embedded DB 기반 Spring Data JPA 동작
- `account-api`: controller mapping, request binding, response wrapping 포함 API wiring
- `supports:error`: 공통 예외와 ErrorCode 동작
- `supports:web`: response wrapping, controller advice, filter writer 동작

## References

- [우아한형제들: 멀티모듈 설계 이야기](https://techblog.woowahan.com/2637/)
- [Spring Boot: Spring Security](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
- [Spring Security: Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security: Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

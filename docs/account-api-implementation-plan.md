# Account API Implementation Plan

이 문서는 `docs/account-api-architecture.md`를 기준으로 이번 주 Account API 구현 진행 상황을 기록하기 위한 작업 목록이다. 작업은 `assignment/week-01-member-account` 브랜치에서 진행하고, 최종 PR 대상은 `loopers-labs/loop-pack-be-l2-vol4-kotlin:shoeone96`로 둔다.

## Progress Board

| Area | Status | Notes |
| --- | --- | --- |
| Module setup | Done | `account-domain/application/persistence/security`, `persistence-core`, `supports:error` 분리 |
| Common web support | Done | `supports:web` 응답 래핑, `supports:error` 예외/ErrorCode, 테스트 fixture 구현 |
| Domain model | Done | `Account`, `AccountCredential`, Embeddable VO 구현 |
| Domain policy | Done | 이메일, 이름, 생년월일, credential identifier/secret, password validator 완료 |
| Persistence | Done | persistence repository interface, JPA adapter, email/credential unique, embedded DataJpaTest 추가 |
| Application use cases | Done | 회원가입, 내 정보 조회, 비밀번호 수정 흐름 구현 |
| Security | Done | `account-security`에 BCrypt encryptor, stateless config, header authentication, entry point 구현 |
| API | Done | 회원가입, 내 정보 조회, 비밀번호 수정 API 구현 |
| Tests | Done | domain/application/persistence/api/security 관련 테스트 통과 |
| Verification | Done | account 관련 모듈 test와 `:apps:account-api:build` 통과. 8080 실행 서버 기준 API 수동 확인 완료 |

## Current Snapshot

- 완료: account 멀티모듈 분리, 공통 error/web 분리, persistence-core 도입, Account/AccountCredential 모델, VO, password validator, 회원가입/내 정보 조회/비밀번호 수정 흐름, `account-security` 기반 header 인증, entry point, 관련 테스트, 8080 실행 서버 기준 API 수동 확인.
- 진행 중: 전체 `./gradlew test` 필요 여부 결정.
- 다음 순서: 필요 시 전체 test 실행 -> assignment branch push -> PR 설명 작성.

## TDD Rule

- 구현 전 `$kent-beck-tdd` 기준으로 테스트 목록을 먼저 작성하고 사용자 컨펌을 받는다.
- 테스트 목록은 MECE하게 작성하되 과하게 쪼개지 않는다.
- 임계값 중심으로 테스트한다. 예: 비밀번호 길이 `7/8/16/17`, 생년월일 포함 여부, 중복 로그인 ID.
- 컨펌 후 Red-Green-Refactor 순서로 진행한다.
- 테스트 우선순위는 `domain policy -> domain model -> application -> API/security integration` 순서로 둔다.
- 반복 사용되는 테스트 객체는 `fixture`에 클래스/팩토리 형태로 둔다.

## API Test Strategy

- `@WebMvcTest`: controller mapping, request/response DTO, JSON binding을 가볍게 검증할 때 사용한다. use case/facade는 mock으로 둔다.
- `@SpringBootTest` + `@AutoConfigureMockMvc`: 전체 Spring context를 띄우되 실제 포트는 열지 않는다. Security filter, ControllerAdvice, ResponseBodyAdvice까지 함께 검증할 때 사용한다.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`: embedded server와 실제 HTTP stack까지 확인할 때 사용한다. 최종 API E2E나 외부 client 관점 검증에만 둔다.
- `@DataJpaTest`: Repository slice 검증에 사용한다. 기본은 embedded DB이며, MySQL 특화 동작 검증이 필요할 때만 Testcontainers/MySQL 설정을 추가한다.
- `account-api` 테스트 프로필은 H2 embedded datasource를 사용한다. `modules:jpa` test fixtures의 MySQL Testcontainers 설정은 기본으로 가져오지 않는다.
- account API/controller/security integration 검증은 실제 포트가 필요하지 않으므로 `@SpringBootTest` + `MockMvc`를 사용한다.
- `V1` suffix와 `/api/v1` route는 명시 요청 전까지 사용하지 않는다.
- request DTO가 해당 controller에서만 쓰이면 controller 파일에 함께 둔다.
- `Command`는 모듈 경계 입력 모델이 필요할 때 사용한다. 현재 회원가입은 `AccountCreateCommand`로 API request와 application 입력을 분리한다.
- `Facade`, security, 추가 repository adapter는 테스트나 요구사항이 필요로 할 때 추가한다.
- controller는 `ApiResponse`를 직접 반환하지 않는다. 성공 응답 wrapping은 `ResponseBodyAdvice`가 담당한다.
- `ApiResponse` 직접 사용은 `ControllerAdvice`, `ResponseBodyAdvice`, filter/security failure writer 같은 공통 web infrastructure로 제한한다.
- 공통 Jackson `NON_NULL` 정책을 따른다. `data = null`인 성공 응답은 `data` 필드가 생략될 수 있다.
- service layer 테스트는 persistence repository interface와 `PasswordEncryptor`를 mock 처리한다. 실제 repository 동작은 `@DataJpaTest`로 분리한다.
- 기본 템플릿 파일은 account 전용 패키지 선호 때문에 수정하지 않는다. account JPA 구현은 `modules:account-persistence`에 두고, 별도 persistence config로 연결한다.

## Remaining Work Estimate

| Order | Task | Size | Notes |
| --- | --- | --- | --- |
| 1 | 전체 test 필요 여부 결정 | M | 시간 비용을 보고 `./gradlew test` 실행 여부 판단 |
| 2 | PR 준비 | S | assignment branch push 후 구현 범위, 테스트 결과, 남은 이슈 기록 |

## 1. Module Setup

- [x] `settings.gradle.kts`에 `:apps:account-api` 포함 여부 확인 및 정리
- [x] `apps/account-api/build.gradle.kts`를 `commerce-api` 패턴에 맞춰 정리
- [x] `spring-boot-starter-security`는 `account-security`에 추가하고 `account-api`는 모듈 의존으로 사용
- [x] `spring-security-web/config/crypto` 직접 의존 분리는 보류. 현재는 `account-security`가 Spring Security 기반 인증/암호화 adapter를 함께 담당하므로 starter 유지
- [x] `spring-boot-docker-compose`는 `account-api`에만 `developmentOnly`로 추가
- [x] 루트 `compose.yaml`을 `jpa.yml` local MySQL 설정과 맞춰 추가
- [x] 공통 의존성은 기존 구조를 따른다: `modules:jpa`, `supports:jackson`, `supports:logging`, `supports:monitoring`, `supports:web`
- [x] `logging.yml`, `monitoring.yml` import 추가
- [x] `spring-boot-starter-actuator` 직접 의존성 제거. Actuator는 `supports:monitoring`을 통해 사용
- [x] Querydsl/kapt는 repository 구현 전까지 제거
- [x] repository slice test용 H2 runtime 의존성 추가
- [x] account-api 테스트에서 `modules:jpa` test fixtures 의존성 제거
- [x] `AccountApiApplication`과 기본 context loading 테스트 확인
- [x] `modules:persistence-core`를 추가해 `BaseEntity`와 최소 Jakarta Persistence API를 분리
- [x] `modules:account-domain`, `modules:account-application`, `modules:account-persistence`, `modules:account-security` 등록
- [x] `supports:error`를 추가해 error code/exception을 web 모듈에서 분리

## 2. Package Skeleton

- [x] `apps/account-api/account/api`: HTTP controller, controller-local request
- [x] `modules/account-application/account/application`: create/authentication/me/password-change service, command, transaction boundary
- [x] `modules/account-domain/account/domain`: model, enum, VO, validator, password encryptor port
- [x] `modules/account-domain/account/domain/error`: account error code
- [x] `modules/account-persistence/account/persistence`: Spring Data JPA repository, adapter, repository config
- [x] `modules/account-security/account/security`: Spring Security config, header filter, entry point, BCrypt password encryptor adapter
- [x] `supports/error`: common error code and exception base
- [x] `supports/web`: response wrapping and web exception handling
- [x] `domain`이 Spring MVC, Security filter, DTO, JPA 구현체를 참조하지 않도록 확인

## 3. Common Web Support

- [x] `:supports:web` 모듈 등록
- [x] `ErrorCode` interface 정의
- [x] `CommonErrorCode` 정의
- [x] `CoreException`과 의미별 예외 타입 정의
- [x] `ApiResponse` 정의: `isSuccess`, `status`, `code`, `message`, `data`, `timestamp`
- [x] `ResponseBodyAdvice` 기반 성공 응답 wrapping 구현
- [x] `StringHttpMessageConverter` 응답 JSON 문자열 처리
- [x] 이미 `ApiResponse`인 응답과 actuator/swagger/api-docs/resource 응답 제외
- [x] `ControllerAdvice` 기반 예외 응답 변환
- [x] filter/security 실패 응답용 `ApiResponseWriter` 구현
- [x] support-web 테스트 fixture 구현
- [x] `:supports:web:build` 통과

## 4. Domain Model

- [x] `Account` 정의
  - [x] `name`
  - [x] `birthDate`
  - [x] `email`
  - [x] email 유니크 제약 추가
  - [x] base columns는 `BaseEntity` 관례를 따른다
- [x] `AccountCredential` 정의
  - [x] `account`
  - [x] `method`
  - [x] `identifier`
  - [x] `secret`
  - [x] base columns는 `BaseEntity` 관례를 따른다
- [x] `CredentialMethod.PASSWORD` 정의
- [x] `method = PASSWORD`일 때 `identifier`는 로그인 ID로 사용
- [x] `secret`에는 암호화된 비밀번호만 저장
- [x] `account_credential(method, identifier)` 유니크 제약 추가
- [x] `account(email)` 유니크 제약 추가
- [x] 회원가입 시 `Account`와 `AccountCredential`을 함께 생성
- [x] 비밀번호 수정은 `AccountCredential.secret`만 변경

## 5. Domain Policy

- [x] `CredentialIdentifier`
  - [x] `PASSWORD` 방식은 영문과 숫자만 허용
  - [x] 빈 값, 공백, 한글, 특수문자 거부
- [x] `AccountName`
  - [x] 이름은 blank 불가
  - [x] 마지막 글자 `*` 마스킹
  - [x] 한 글자 이름 처리 규칙 확정
- [x] `birthDate`
  - [x] 입력 포맷은 API DTO `LocalDate` binding으로 처리
  - [x] 존재하지 않는 날짜는 DTO 파싱 단계에서 거부
  - [x] 미래 날짜는 service layer에서 거부
- [x] `Email`
  - [x] 이메일 정규식 검증
  - [x] 빈 값, 도메인 누락, 로컬 파트 누락 거부
- [x] `PasswordValidator`
  - [x] 8~16자 허용
  - [x] 영문 대/소문자, 숫자, 특수문자만 허용
  - [x] 생년월일 문자열 포함 거부
  - [x] 현재 비밀번호와 동일한 새 비밀번호 거부는 `AccountPasswordChangeService`에서 처리

## 6. Persistence

- [x] persistence repository interface 정의
  - [x] `AccountRepository`
  - [x] `AccountCredentialRepository`
- [x] Spring Data JPA repository 정의
  - [x] `AccountJpaRepository`
  - [x] `AccountCredentialJpaRepository`
- [x] repository adapter 구현
  - [x] `AccountRepositoryImpl`
  - [x] `AccountCredentialRepositoryImpl`
- [x] `AccountPersistenceConfig`로 account persistence repository scan 구성
- [x] `method + identifier` 조회 메서드 구현
- [x] 로그인 ID 중복 여부 조회 구현
- [x] email 중복 여부 조회 구현
- [ ] 유니크 제약 위반을 도메인/애플리케이션 예외로 변환
- [ ] soft delete가 필요한 경우 기존 `BaseEntity` 관례에 맞춰 처리

## 7. Application Use Cases

- [x] 회원가입
  - [x] controller-local request 사용: 로그인 ID, 비밀번호, 이름, 생년월일, 이메일
  - [x] `AccountCreateCommand`로 API request와 application 입력 분리
  - [x] 입력 정책 검증
  - [x] 로그인 ID 중복 검사
  - [x] 이메일 중복 검사
  - [x] 비밀번호 암호화
  - [x] account와 credential 저장
- [x] 내 정보 조회
  - [x] 인증된 account 기준 조회
  - [x] 반환 정보: 로그인 ID, 이름, 생년월일, 이메일
  - [x] 이름 마지막 글자 마스킹
- [x] 비밀번호 수정
  - [x] 기존 비밀번호 검증
  - [x] 새 비밀번호 정책 검증
  - [x] 현재 비밀번호 재사용 거부
  - [x] 새 비밀번호 암호화 후 저장
- [x] 트랜잭션 경계는 application 계층에 둔다

## 8. Spring Security

- [x] stateless security config 구성
- [x] form login 비활성화
- [x] default basic login 비활성화
- [x] 회원가입 API는 `permitAll`
- [x] 내 정보 조회, 비밀번호 수정 API는 인증 필요
- [x] `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 상수 정의
- [x] custom authentication filter 구현
- [x] 로그인 ID/비밀번호로 credential 조회 및 비밀번호 검증
- [x] 인증 성공 시 account 식별자를 principal/request attribute에 담는다
- [x] 인증 실패 응답 정책 확정 및 구현
- [x] domain에는 `PasswordEncryptor` port만 노출
- [x] `account-security`에서 Spring Security `PasswordEncoder`로 구현

## 9. API Layer

- [x] URI 확정
  - [x] `POST /accounts`
  - [x] `GET /accounts/me`
  - [x] `PATCH /accounts/me/password`
- [x] 회원가입 request DTO 정의
- [x] response DTO 정의
- [x] 회원가입 controller request를 service command로 변환
- [x] 조회/수정 controller request를 service로 전달하고 필요한 response DTO로 변환
- [x] `supports:web`의 `ApiResponse` 패턴 준비
- [x] `ErrorCode` interface와 domain별 error code enum 방식 준비
- [x] API별 request/response mapping에서 오류 응답 확인
- [ ] OpenAPI annotation은 기존 `commerce-api` 스타일을 따른다

## 10. Test Inventory

구현 전 아래 목록을 기준으로 실제 테스트 목록을 먼저 제안하고 컨펌받는다.

- [x] `PasswordValidatorTest`
  - [x] 정상 비밀번호 성공
  - [x] 길이 7 실패, 16 초과 실패
  - [x] 허용되지 않은 문자 실패
  - [x] 생년월일 `yyyyMMdd`, `yyMMdd` 포함 실패
  - [x] 현재 비밀번호 재사용 실패는 `AccountPasswordChangeServiceTest`에서 검증
- [x] `CredentialIdentifierTest`
  - [x] 영문/숫자 성공
  - [x] 공백, 한글, 특수문자 실패
- [x] `AccountNameTest`
  - [x] 정상 이름 생성
  - [x] blank 실패
  - [x] 한 글자/두 글자 이상 마스킹
- [x] `EmailTest`
  - [x] 정상 이메일 성공
  - [x] 빈 값, 도메인 누락, 로컬 파트 누락 실패
- [x] `CredentialSecretTest`
  - [x] 암호화된 비밀번호 값 성공
  - [x] blank 실패
- [x] `AccountTest`
  - [x] 정상 account 생성
  - [x] 이름 마스킹
- [x] `AccountCredentialTest`
  - [x] `PASSWORD` credential 생성
  - [x] 비밀번호 변경
- [x] `AccountCreateServiceTest`
  - [x] 회원가입 성공 시 account/credential 저장
  - [x] 중복 로그인 ID 실패
  - [x] 중복 이메일 실패
- [x] `AccountDataRepositoryTest`
  - [x] credential 저장 후 `method + identifier` 존재 여부 조회
  - [x] account 저장 후 email 존재 여부 조회
  - [x] 중복 email 저장 시 DB unique 제약 실패
- [x] `AccountMeServiceTest`
  - [x] 내 정보 조회 성공
  - [x] 이름 마스킹 반환
- [x] `AccountPasswordChangeServiceTest`
  - [x] 비밀번호 수정 성공
  - [x] credential 없음 실패
  - [x] 기존 비밀번호 불일치 실패
  - [x] 새 비밀번호 정책 실패
  - [x] 현재 비밀번호 재사용 실패
- [x] `AccountSecurityIntegrationTest`
  - [x] 회원가입은 인증 헤더 없이 성공
  - [x] 보호 API는 헤더 없으면 실패
  - [x] 보호 API는 올바른 헤더면 성공
  - [x] 잘못된 비밀번호면 실패
- [x] `ApiResponseTest`
  - [x] 성공 응답 shape
  - [x] 실패 응답 shape
- [x] `ApiResponseBodyAdviceTest`
  - [x] 일반 객체 wrapping
  - [x] 이미 wrapped 응답 제외
  - [x] String 응답 JSON 처리
  - [x] actuator 응답 제외
- [x] `CoreExceptionTest`
  - [x] ErrorCode 보존
  - [x] custom message 처리

## 11. Verification

- [x] `./gradlew :apps:account-api:test`
- [x] `./gradlew :apps:account-api:build`
- [x] `./gradlew :apps:account-api:test --tests '*AccountControllerTest' --tests '*AccountSecurityIntegrationTest'`
- [x] `./gradlew :modules:account-security:test :modules:account-application:test :modules:account-persistence:test :apps:account-api:test`
- [x] `./gradlew :modules:account-security:ktlintCheck :apps:account-api:ktlintCheck :apps:account-api:build`
- [x] `./gradlew :supports:web:test`
- [x] `./gradlew :supports:web:build`
- [x] `./gradlew :supports:error:test :supports:web:test :modules:account-domain:test :modules:account-application:test :modules:account-persistence:test --no-daemon`
- [ ] `./gradlew test`
- [x] 8080 실행 서버 확인
- [x] API 수동 확인
- [x] `git status --short`로 의도하지 않은 변경 확인
- [ ] assignment branch push
- [ ] PR 설명에 구현 범위, 테스트 결과, 남은 이슈 기록

### Manual API Verification on 8080

2026-05-15에 사용자가 다시 실행한 로컬 서버 `http://127.0.0.1:8080` 기준으로 확인했다.

사전 확인:

- `lsof -nP -iTCP:8080 -sTCP:LISTEN` 결과 `java` 프로세스가 8080을 listen 중이었다.
- `GET /v3/api-docs` 결과 현재 8080 서버에는 account API만 노출되어 있었다.
- 노출 경로는 `POST /accounts`, `GET /accounts/me`, `PATCH /accounts/me/password`였다.

실행 계정:

- `loginId`: `codex5151536`
- `email`: `codex5151536@example.com`
- `birthDate`: `1990-05-15`
- 인증 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

시나리오 결과:

| Step | Request | Expected | Actual |
| --- | --- | --- | --- |
| 1 | `POST /accounts` | 회원가입 성공 | `200 OK`, `isSuccess=true` |
| 2 | `GET /accounts/me` with valid password | 내 정보 조회 성공 | `200 OK`, masked name, email, birthDate 반환 |
| 3 | `GET /accounts/me` without auth | 인증 실패 | `401 COMMON:UNAUTHORIZED` |
| 4 | `GET /accounts/me` with wrong password | 인증 실패 | `401 COMMON:UNAUTHORIZED` |
| 5 | `PATCH /accounts/me/password` with valid current password | 비밀번호 변경 성공 | `200 OK`, `isSuccess=true` |
| 6 | `GET /accounts/me` with old password after change | 기존 비밀번호 인증 실패 | `401 COMMON:UNAUTHORIZED` |
| 7 | `GET /accounts/me` with new password after change | 새 비밀번호 인증 성공 | `200 OK`, 내 정보 반환 |
| 8 | duplicate `POST /accounts` | 중복 가입 실패 | `409 ACCOUNT:DUPLICATE_LOGIN_ID` |

관찰 결과:

- 가입, 인증, 내 정보 조회, 비밀번호 변경, 기존 비밀번호 무효화, 새 비밀번호 인증 흐름은 기대대로 동작했다.
- 미인증/잘못된 비밀번호/중복 로그인 ID 오류는 공통 `ApiResponse` 실패 형식으로 반환되었다.
- 현재 8080 서버는 `account-api`로 확인되었으므로 `commerce-api`의 `/api/v1/examples/{id}`는 이 수동 검증 범위에서 제외한다.

## Open Questions

- [x] 이름 VO는 우선 blank만 거부한다
- [x] 생년월일은 미래 날짜를 거부한다
- [x] 생년월일 API 입력 포맷 확정
- [x] `ErrorType` 대신 `ErrorCode` interface와 domain별 enum을 사용한다
- [x] 인증 실패 세부 ErrorCode 결정: `CommonErrorCode.UNAUTHORIZED`
- [x] API URI를 위 제안대로 확정
- [x] 한 글자 이름 마스킹 결과는 `*`로 한다

## Change Log

| Date | Change | Notes |
| --- | --- | --- |
| 2026-05-14 | Initial implementation plan | Architecture decision 기반 TODO 작성 |
| 2026-05-14 | Domain model and VO tests | Embeddable VO, Account, AccountCredential 구현 |
| 2026-05-14 | Common web support and dependency cleanup | `supports:web`, response wrapping, ErrorCode, fixture, account-api 의존성 정리 |
| 2026-05-15 | Signup persistence flow | repository interface/adapter, BCrypt encryptor, embedded DataJpaTest, H2 test profile 추가 |
| 2026-05-15 | Account package cleanup | account 패키지 재구성, Model suffix 제거, 기본 JpaConfig 변경 없이 repository 위치 정리 |
| 2026-05-15 | Account module split | `account-domain/application/persistence/security`, `persistence-core`, `supports:error` 분리 |
| 2026-05-15 | Header authentication | `account-security`에 stateless security config, header filter, entry point, password matching 구현 |
| 2026-05-15 | Manual API verification | 8080 실행 서버 기준 회원가입, 인증, 내 정보 조회, 비밀번호 변경, 오류 응답 시나리오 확인 |

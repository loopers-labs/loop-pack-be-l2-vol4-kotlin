# 테스트 컨벤션

`tdd-practice` 레퍼런스를 기반으로 한 본 프로젝트의 테스트 작성 규칙. 신규 도메인 추가 시 본 문서를 따른다.

## 1. 파일 분류 규칙

테스트는 **세 가지 파일**로 분류한다. 기본 구분은 **파일명 suffix**이며, 디렉토리 분류는 도메인별로 선택 적용한다.

| 종류 | 파일명 패턴 | 어노테이션 | 설명 |
|---|---|---|---|
| 단위 | `<Aggregate>ModelTest.kt`, `<VO>Test.kt` 등 | (없음) | Spring 컨텍스트 없이 POJO 단독 검증. 의존성은 `mockk` 또는 단순 fake. |
| 통합 | `<Service>IntegrationTest.kt` | `@SpringBootTest` | DB 포함 실제 빈 와이어링. `@AfterEach databaseCleanUp.truncateAllTables()`. |
| E2E/API | `<Aggregate>ApiE2ETest.kt` | `ApiTest` 상속 | HTTP 호출까지 포함. `RANDOM_PORT` + `TestRestTemplate`. |

위치:
- 기본 단위/통합: `src/test/kotlin/<package>/domain/<aggregate>/`
- 분류 적용 도메인 단위: `src/test/kotlin/<package>/domain/<aggregate>/unit/`
- 분류 적용 도메인 통합: `src/test/kotlin/<package>/domain/<aggregate>/integration/`
- 분류 적용 도메인 지원 코드: `src/test/kotlin/<package>/domain/<aggregate>/support/`
- E2E/API: `src/test/kotlin/<package>/interfaces/api/`

현재 `user` 도메인은 `unit/`, `integration/`, `support/` 분류를 적용한다. 이 분류는 기존 `src/test` 내부 package 정리이며 별도 Gradle source set 을 추가하지 않는다. E2E/API 테스트는 계속 `interfaces/api` 에 두고 `ApiTest` 를 상속한다.

## 2. 메서드 네이밍 규칙

- 한국어 백틱 메서드명을 사용한다. `@DisplayName`, `@Nested`, `@DisplayNameGeneration` 등은 사용하지 않는다.
- 형식: `` `<상황>_<기대결과>`() `` — Given/When/Then을 한 줄로 녹여낸다.
- 띄어쓰기는 `_`로 표기.

예:
```kotlin
@Test fun `모든_필드가_유효하면_회원이_정상_생성된다`() { ... }
@Test fun `중복_로그인ID로_가입하면_CONFLICT가_발생한다`() { ... }
@Test fun `비밀번호에_생년월일이_포함되면_BAD_REQUEST가_발생한다`() { ... }
```

## 3. Steps 클래스 패턴

도메인 객체/요청 객체의 생성을 한 곳에 모은다. 기본값 활용으로 테스트 코드의 보일러플레이트를 제거한다.

- 파일명: `<Aggregate>Steps.kt`
- 위치: 기본 `src/test/kotlin/<package>/domain/<aggregate>/`, 분류 적용 도메인은 `src/test/kotlin/<package>/domain/<aggregate>/support/`
- 구조: `class <Aggregate>Steps { companion object { ... } }` (또는 `object`도 가능)
- 메서드는 한국어 백틱 + 기본값 인자
- 책임: 객체/요청 빌더만. (RestAssured 같은 HTTP 호출 헬퍼는 본 프로젝트에서는 사용하지 않음 → TestRestTemplate 직접)

예 (`UserSteps`):
```kotlin
class UserSteps {
    companion object {
        const val 기본_로그인_ID = "user1234"
        const val 기본_비밀번호 = "Password1!"
        val 기본_생년월일 = LocalDate.of(1990, 5, 14)

        fun 회원_도메인_생성(...): UserModel = ...
        fun 회원가입_커맨드_생성(...): UserSignUpCommand = ...
        fun 회원가입_요청_생성(...): SignUpRequest = ...
    }
}
```

## 4. ApiTest 베이스 클래스

E2E/API 테스트는 `com.loopers.ApiTest`를 상속한다. 베이스가 `@SpringBootTest(RANDOM_PORT)`, `TestRestTemplate`/`DatabaseCleanUp` 주입, `@AfterEach` 정리를 모두 책임진다.

```kotlin
class UserApiE2ETest
    @Autowired constructor(
        private val userService: UserService,
    ) : ApiTest() {
        @Test
        fun `유효한_요청이면_201_CREATED를_반환한다`() {
            testRestTemplate.exchange(...)
            ...
        }
    }
```

베이스의 `@AfterEach tearDown()`을 하위 클래스에서 재정의·중복 선언하지 않는다.

## 5. AssertJ

`org.assertj.core.api.Assertions.assertThat`을 사용한다. `assertAll`, `kotlin.test.assertEquals` 등은 사용하지 않는다.

## 6. 회귀 검증

신규 테스트 추가 시 기존 도메인(Example 등) 테스트가 함께 통과하는지 다음으로 확인한다.

```
./gradlew :apps:commerce-api:test
```

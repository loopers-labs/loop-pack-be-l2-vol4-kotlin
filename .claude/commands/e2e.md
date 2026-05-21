# E2E 테스트 작성

사용자가 제공한 요구사항: **$ARGUMENTS**

## 진행 절차 (반드시 순서대로)

### Step 1. 요구사항 분석 & 코드 탐색

1. 사용자가 입력한 요구사항($ARGUMENTS)을 분석하세요.
2. 요구사항과 관련된 **컨트롤러, DTO, 서비스, 도메인 모델, Repository** 코드를 모두 읽으세요.
3. 다음을 파악하세요:
   - 엔드포인트 URL, HTTP 메서드
   - 요청/응답 DTO와 validation 어노테이션 (@NotBlank, @Size, @Pattern 등)
   - 서비스 레이어의 비즈니스 규칙과 예외 발생 조건
   - 도메인 모델의 제약 조건

### Step 2. 테스트 케이스 목록 제안 (사용자 확인 필수)

분석 결과를 바탕으로, 아래 형식의 **테스트 케이스 목록**을 사용자에게 제시하세요.

```
## 테스트 케이스 목록

**대상 API:** `POST /api/v1/user/signup`
**테스트 클래스:** `UserV1ApiE2ETest`

### 정상 케이스
- [ ] 유효한 정보로 요청하면 성공 응답을 받는다

### 유효성 검사 실패 (400 BAD_REQUEST)
- [ ] 비밀번호가 8자 미만이면 실패한다
- [ ] 이메일 형식이 올바르지 않으면 실패한다
- [ ] 필수 필드가 비어있으면 실패한다

### 비즈니스 예외
- [ ] 이미 존재하는 ID로 가입하면 실패한다

### 경계값
- [ ] ID가 최대 길이(20자)이면 성공한다
```

그리고 반드시 사용자에게 다음을 질문하세요:

> **이 테스트 케이스로 진행할까요? 추가/제거/수정할 케이스가 있으면 알려주세요.**

**Step 3로 절대 넘어가지 마세요. 사용자가 명시적으로 승인("진행", "ㅇㅇ", "좋아", "OK" 등)할 때까지 기다리세요.**

### Step 3. 테스트 코드 작성

사용자가 승인한 케이스만 작성하세요. 아래 컨벤션을 따릅니다.

---

## 테스트 클래스 컨벤션

### 파일 위치 & 네이밍
- 경로: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/`
- 클래스명: `{ControllerName}E2ETest` (예: `UserV1ApiE2ETest`)

### 클래스 구조

#### POST API 예시
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class {Name}E2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api/v1/..."
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // --- 요청 헬퍼 ---
    private fun buildRequest(
        field1: String = "default1",
        field2: String = "default2",
    ): Map<String, String> = mapOf(
        "field1" to field1,
        "field2" to field2,
    )

    // HTTP 호출 헬퍼 (TestRestTemplate + ParameterizedTypeReference 사용)
    private fun postEndpoint(body: Map<String, String>): ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, HttpEntity(body, headers), responseType)
    }

    @DisplayName("POST /api/v1/...")
    @Nested
    inner class MethodName {
        @DisplayName("유효한 요청이면, 성공 응답을 받는다.")
        @Test
        fun returnsSuccess_whenValidRequest() {
            // arrange
            val request = buildRequest()

            // act
            val response = postEndpoint(request)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }
    }
}
```

#### GET API (Path Variable) 예시
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class {Name}E2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val someJpaRepository: SomeJpaRepository, // 테스트 데이터 사전 세팅용
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        // Path variable이 있는 엔드포인트는 람다로 정의
        private val ENDPOINT_GET: (Long) -> String = { id: Long -> "/api/v1/resources/$id" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/resources/{id}")
    @Nested
    inner class Get {
        @DisplayName("존재하는 ID를 주면, 해당 정보를 반환한다.")
        @Test
        fun returnsInfo_whenValidIdIsProvided() {
            // arrange — JpaRepository로 테스트 데이터 직접 저장
            val saved = someJpaRepository.save(SomeEntity(name = "테스트"))
            val requestUrl = ENDPOINT_GET(saved.id)

            // act — GET 요청 시 HttpEntity<Any>(Unit) 사용
            val responseType = object : ParameterizedTypeReference<ApiResponse<SomeDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert — response.body?.data 로 응답 데이터 필드 검증
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(saved.id) },
                { assertThat(response.body?.data?.name).isEqualTo(saved.name) },
            )
        }

        @DisplayName("존재하지 않는 ID를 주면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsException_whenInvalidIdIsProvided() {
            // arrange
            val requestUrl = ENDPOINT_GET(-1L)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<SomeDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
```

### 필수 import
```kotlin
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
```

## Assertion 패턴

- `assertAll()` 로 상태코드 + 응답 body를 함께 검증
- 성공: `response.statusCode.is2xxSuccessful` + `ApiResponse.Metadata.Result.SUCCESS`
- 실패: `response.statusCode` == `HttpStatus.BAD_REQUEST` + `ApiResponse.Metadata.Result.FAIL`
- 에러 메시지 검증이 필요하면 `response.body?.meta?.message` 도 확인

## 주의사항

- **프로덕션 코드 생성/수정 절대 금지** - 이 스킬은 테스트 코드만 작성한다. 컨트롤러, 서비스, DTO, 엔티티 등 프로덕션 코드는 읽기만 하고 절대 생성하거나 수정하지 않는다.
- Mock 사용 금지 - 실제 DB를 사용하는 E2E 테스트만 작성
- println 금지
- 테스트 메서드명은 영어, `@DisplayName`은 한국어로 작성
- 각 테스트는 독립적이어야 함 (`@AfterEach`에서 DB 초기화)
- GET 요청의 경우 `HttpEntity<Any>(Unit)`을 body로 사용
- Path variable이 있는 엔드포인트는 `companion object`에 `val ENDPOINT: (Long) -> String` 람다로 정의
- 조회 테스트 등 사전 데이터가 필요하면, 관련 **JpaRepository를 직접 주입**받아 `save()`로 데이터 세팅
- 응답 데이터 검증 시 `response.body?.data?.field` 패턴 사용

### Step 4. 테스트 실행 & 검증

1. `./gradlew test` 로 테스트 실행하여 **모든 테스트 통과** 확인
2. ktlint 위반이 없는지 확인
3. 실패하는 테스트가 있으면 원인을 분석하고 수정

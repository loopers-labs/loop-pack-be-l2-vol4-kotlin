# 통합 테스트 작성

$ARGUMENTS 에 대한 통합 테스트를 작성해주세요.

## 규칙

1. **테스트 대상 클래스를 먼저 읽고**, 공개 메서드 시그니처·반환값·예외를 파악하세요.
2. 관련된 도메인 모델, Repository, 의존 서비스 코드도 읽어서 **비즈니스 규칙과 예외 케이스**를 파악하세요.
3. 아래 컨벤션을 반드시 따르세요.

## 테스트 클래스 컨벤션

### 파일 위치 & 네이밍
- Service 테스트: `apps/commerce-api/src/test/kotlin/com/loopers/application/{domain}/{ClassName}IntegrationTest.kt`
- Repository 테스트: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/{domain}/{ClassName}IntegrationTest.kt`
- 클래스명: `{ClassName}IntegrationTest` (예: `UserServiceIntegrationTest`, `UserRepositoryImplIntegrationTest`)

### 클래스 구조
```kotlin
@SpringBootTest
class {Name}IntegrationTest @Autowired constructor(
    private val targetUnderTest: TargetClass,
    private val databaseCleanUp: DatabaseCleanUp,
    // 필요 시 추가 의존성 주입 (JpaRepository, 다른 서비스 등)
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // --- 팩토리 헬퍼 ---
    // 기본값이 있는 파라미터로 도메인 객체를 생성하는 함수
    private fun createEntity(
        field1: String = "default1",
        field2: String = "default2",
    ): DomainEntity = DomainEntity.create(
        field1 = field1,
        field2 = field2,
    )

    // --- 테스트 그룹 (메서드별 @Nested) ---
    @DisplayName("{메서드명}을 호출할 때,")
    @Nested
    inner class MethodName {

        @DisplayName("정상 케이스 설명")
        @Test
        fun successCase_whenCondition() {
            // arrange
            val entity = createEntity()

            // act
            val result = targetUnderTest.method(entity)

            // assert
            assertThat(result.field).isEqualTo("expected")
        }

        @DisplayName("예외 케이스 설명")
        @Test
        fun throwsException_whenCondition() {
            // arrange
            val entity = createEntity(field1 = "invalid")

            // act
            val exception = assertThrows<CoreException> {
                targetUnderTest.method(entity)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

### 필수 import
```kotlin
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
```

## 테스트 케이스 작성 기준

### Service 테스트
1. **정상 케이스** - 유효한 입력으로 메서드가 정상 동작하는지 확인
2. **비즈니스 예외** - 중복, 유효성 검사 실패 등 도메인 규칙 위반 시 `CoreException` 발생 확인
3. **경계값** - 필드 제약 조건, 포맷 제약 등

### Repository 테스트
1. **저장 성공** - 새 엔티티 저장 후 ID 부여 및 필드 값 검증
2. **조회** - 저장된 데이터를 정확히 조회하는지 확인
3. **예외 케이스** - 잘못된 입력이나 존재하지 않는 데이터 접근 시 예외 확인
4. **JPA 연관관계** - 연관 엔티티가 있는 경우 함께 저장/조회되는지 확인

## Assertion 패턴

- 여러 필드를 검증할 때는 `assertAll()` 사용
- 예외 검증: `assertThrows<CoreException>` + `assertThat(exception.errorType).isEqualTo(ErrorType.XXX)`
- 단일 값 검증: `assertThat(result).isEqualTo(expected)`
- 컬렉션 검증: `assertThat(list).hasSize(n)`, `assertThat(list).extracting("field").containsExactly(...)`

## 주의사항

- **절대 프로덕션 코드 생성 금지** - 테스트 코드만 작성할 것
- Mock 사용 금지 - `@SpringBootTest`로 실제 빈을 주입받아 테스트
- println 금지
- 테스트 메서드명은 영어, `@DisplayName`은 한국어로 작성
- 각 테스트는 독립적이어야 함 (`@AfterEach`에서 DB 초기화)
- 도메인 객체 생성 시 `User.create()` 같은 팩토리 메서드가 있으면 그것을 사용하고, 없으면 생성자를 직접 호출
- 팩토리 헬퍼 함수의 기본값은 유효한 값으로 설정

## 작성 완료 후

1. `./gradlew test` 로 테스트 실행하여 통과 확인
2. ktlint 위반이 없는지 확인

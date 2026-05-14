# 단위 테스트 작성

$ARGUMENTS 에 대한 단위 테스트를 작성해주세요.

## 규칙

1. **테스트 대상 클래스를 먼저 읽고**, 공개 메서드 시그니처·반환값·예외를 파악하세요.
2. 관련된 도메인 모델, 의존 서비스 코드도 읽어서 **비즈니스 규칙과 예외 케이스**를 파악하세요.
3. 아래 컨벤션을 반드시 따르세요.

## 테스트 클래스 컨벤션

### 파일 위치 & 네이밍
- Domain 테스트: `apps/commerce-api/src/test/kotlin/com/loopers/domain/{domain}/{ClassName}Test.kt`
- Service 테스트: `apps/commerce-api/src/test/kotlin/com/loopers/application/{domain}/{ClassName}Test.kt`
- 클래스명: `{ClassName}Test` (예: `UserTest`, `UserServiceTest`)

### Domain 단위 테스트 구조
```kotlin
class {Name}Test {

    // --- 팩토리 헬퍼 (필요 시) ---
    private fun createEntity(
        field1: String = "default1",
        field2: String = "default2",
    ): DomainEntity = DomainEntity.create(
        field1 = field1,
        field2 = field2,
    )

    // --- 테스트 그룹 (메서드 또는 행위별 @Nested) ---
    @DisplayName("{행위}할 때, ")
    @Nested
    inner class MethodName {

        @DisplayName("정상 케이스 설명")
        @Test
        fun successCase_whenCondition() {
            // arrange
            val input = "validInput"

            // act
            val result = DomainEntity.create(field1 = input)

            // assert
            assertThat(result.field1).isEqualTo(input)
        }

        @DisplayName("예외 케이스 설명")
        @Test
        fun throwsException_whenCondition() {
            // arrange
            val input = "invalidInput"

            // act
            val result = assertThrows<CoreException> {
                DomainEntity.create(field1 = input)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

### Service 단위 테스트 구조 (MockK 사용)
```kotlin
class {Name}ServiceTest {
    private val someRepository: SomeRepository = mockk()
    private val someService = SomeService(someRepository)

    // --- 팩토리 헬퍼 ---
    private fun createEntity(
        field1: String = "default1",
        field2: String = "default2",
    ): DomainEntity = DomainEntity.create(
        field1 = field1,
        field2 = field2,
    )

    @DisplayName("{메서드명}을 호출할 때,")
    @Nested
    inner class MethodName {

        @DisplayName("의존 객체의 {메서드}가 실행된다.")
        @Test
        fun callsDependencyMethod() {
            // arrange
            val entity = createEntity()
            every { someRepository.someMethod(any()) } returns entity

            // act
            someService.doSomething(entity)

            // assert
            verify { someRepository.someMethod(any()) }
        }

        @DisplayName("조건이 맞지 않으면, BAD_REQUEST 에러가 발생한다.")
        @Test
        fun throwsBadRequest_whenConditionFails() {
            // arrange
            val entity = createEntity()
            every { someRepository.findById(any()) } throws CoreException(ErrorType.NOT_FOUND)

            // act
            val exception = assertThrows<CoreException> {
                someService.doSomething(entity)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
```

### 필수 import (Domain 테스트)
```kotlin
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
```

### 필수 import (Service 테스트 - MockK 사용)
```kotlin
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
```

## 테스트 케이스 작성 기준

### Domain 테스트
1. **생성 성공** - 유효한 입력으로 도메인 객체가 정상 생성되는지 확인
2. **유효성 검사 실패** - 도메인 제약 조건 위반 시 `CoreException` 발생 확인
3. **경계값** - 필드 길이 제한, 최소/최대값 등 경계 조건 테스트
4. **도메인 로직** - 상태 변경, 계산 등 도메인 메서드의 동작 검증

### Service 테스트 (MockK)
1. **의존 객체 호출 검증** - `verify`로 Repository 등 의존 객체의 메서드가 호출되는지 확인
2. **비즈니스 예외** - 중복 검사, 존재 여부 확인 등 비즈니스 규칙 위반 시 예외 발생 확인
3. **반환값 검증** - 서비스 메서드의 반환값이 기대와 일치하는지 확인

## Assertion 패턴

- 단일 값 검증: `assertThat(result).isEqualTo(expected)`
- 예외 검증: `assertThrows<CoreException>` + `assertThat(exception.errorType).isEqualTo(ErrorType.XXX)`
- 여러 필드를 검증할 때는 `assertAll()` 사용
- MockK 호출 검증: `verify { repository.method(any()) }`
- 컬렉션 검증: `assertThat(list).hasSize(n)`, `assertThat(list).extracting("field").containsExactly(...)`

## 주의사항

- **절대 프로덕션 코드 생성/수정 금지** - 테스트 코드만 작성할 것
- Domain 테스트에는 Mock 사용 금지 - 순수 단위 테스트로 작성
- Service 테스트에서는 MockK만 사용 (`mockk()`, `every`, `verify`)
- Spring Context를 로드하지 않음 (`@SpringBootTest` 사용 금지)
- println 금지
- 테스트 메서드명은 영어, `@DisplayName`은 한국어로 작성
- 각 테스트는 독립적이어야 함
- 도메인 객체 생성 시 `Entity.create()` 같은 팩토리 메서드가 있으면 그것을 사용하고, 없으면 생성자를 직접 호출
- 팩토리 헬퍼 함수의 기본값은 유효한 값으로 설정
- `// arrange`, `// act`, `// assert` 주석으로 테스트 단계 구분

## 작성 완료 후

1. `./gradlew test` 로 테스트 실행하여 통과 확인
2. ktlint 위반이 없는지 확인

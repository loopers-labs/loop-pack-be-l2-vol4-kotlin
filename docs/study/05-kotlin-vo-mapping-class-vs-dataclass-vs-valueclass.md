# Kotlin + JPA Value Object 매핑 — 일반 class vs data class vs value class

> 대상 환경: Kotlin 2.0.20 / Spring Boot 3.4.4 (→ Hibernate ORM 6.6.x) / Java 21.
> 이 저장소는 `kotlin("plugin.jpa")`(no-arg)와 `kotlin("plugin.spring")`(all-open)이 적용되어 있습니다
> (`apps/commerce-api/build.gradle.kts`, `modules/account-domain/build.gradle.kts`에서 `id("org.jetbrains.kotlin.plugin.jpa")` 확인).
>
> 표기 규칙: 각 사실 주장 뒤의 `[n]`은 문서 말미 출처 목록의 번호입니다.
> "직접 확인" = 원문을 fetch해 확인, "미확인" = 1차 출처 fetch 실패로 추가 검증 필요.

---

## 1. 한 줄 요약 (결론 먼저)

- **단일/소수 값으로 구성된 Value Object를 한 엔티티 테이블의 컬럼(들)에 묻어 넣을 때**: `@Embeddable` + **data class**가 가장 잘 맞습니다. VO의 본질은 "값 동등성"이고, data class가 자동 생성하는 모든 프로퍼티 기반 `equals`/`hashCode`가 바로 그 의미와 일치하기 때문입니다 (이 저장소의 `BrandName`이 이 방식).
- **value class(`@JvmInline`)는 `@Embeddable`로 매핑할 수 없습니다.** value class는 항상 `final`이고 backing field가 없어서, Hibernate가 요구하는 no-arg 인스턴스화 + 컬럼 매핑이 성립하지 않습니다 [1][2].
- value class를 단일 컬럼에 영속화하려면 **엔티티의 직접 필드 + (필요 시) AttributeConverter** 경로를 씁니다. 도메인 순수성/제로 오버헤드 래퍼(예: ID 타입)에는 매력적이지만, JVM name mangling과 nullable 처리에서 엣지케이스가 있습니다 [3][7].
- **data class를 피하라"는 통념은 `@Entity`에 한정**됩니다. 그 근거(아래 §4)는 전부 엔티티의 프록시/식별자/dirty-checking 특성에서 나오며, ID도 없고 mutable일 필요도 없는 `@Embeddable` VO에는 그대로 적용되지 않습니다 [4][5].

### 의사결정 표

| 상황 | 권장 | 매핑 | 비고 |
|---|---|---|---|
| 영속화하지 않는 순수 도메인 전용 VO (DB와 무관) | data class **또는** value class | (매핑 없음) | 단일 값 + 제로 오버헤드면 value class, 다중 값이면 data class |
| 엔티티 테이블 컬럼에 묻는 VO (이 과제의 BrandName 등) | **`@Embeddable` data class** | `@Embeddable` + `@Column` + `init` 검증 | 이 저장소 권장안 (§7) |
| 단일 값 VO를 한 컬럼에 + 도메인 순수 래퍼 유지 | value class (또는 일반 class) | 엔티티 직접 필드 + `AttributeConverter` | name mangling/nullable 주의 [3][7] |
| 영속 대상 엔티티(식별자·생명주기 보유) | **일반 class (open)** | `@Entity` | data class 금지 (§4) [4][5] |

---

## 2. 배경 — Value Object와 "값 동등성"

Value Object(VO)는 **고유 식별자가 없고, 보유한 값 자체로 동일성이 결정되는** 도메인 객체입니다. `Email("a@b.com")`과 또 다른 `Email("a@b.com")`은 서로 다른 인스턴스여도 **같은 값이면 같은 것**으로 취급되어야 합니다. 이것을 값 동등성(value/structural equality)이라 부르며, 참조 동등성(reference equality, `===`)과 대비됩니다.

반대로 Entity는 **식별자(ID)로 동일성이 결정**됩니다. 같은 ID면 필드 값이 달라도 같은 엔티티이고, 다른 ID면 모든 필드가 같아도 다른 엔티티입니다. 이 차이가 뒤(§4, §5)에서 "data class를 엔티티에 쓰면 안 되지만 VO에는 적합하다"는 비대칭의 근원입니다.

Kotlin에서 값 동등성을 손쉽게 얻는 두 가지 도구가 **data class**와 **value class**이며, JPA 매핑 가능 여부에서 갈립니다.

이 저장소의 기존 VO 컨벤션(`modules/account-domain/.../vo/Email.kt`, `AccountName.kt`)은 일반 `class` + 수동 `equals`/`hashCode`였고, `BrandName`(`apps/commerce-api/.../domain/brand/BrandName.kt`)부터 `@Embeddable data class`로 전환했습니다.

---

## 3. 세 가지 선택지 비교

| 항목 | 일반 class | data class | value class (`@JvmInline`) |
|---|---|---|---|
| `equals`/`hashCode` | **수동 작성** 필요 | 모든 프로퍼티 기반 **자동 생성** | underlying 값 기반 자동 (단일 프로퍼티) |
| 불변성 | 자유 (val/var) | 자유 (보통 val) | 사실상 불변 (프로퍼티 1개) |
| 프로퍼티 개수 | 제한 없음 | 제한 없음 | **정확히 1개** (primary 생성자) [1] |
| backing field | 가능 | 가능 | **불가** — computed property만 [1] |
| 상속/`open` | 가능 | `final` (open 불가) | **항상 `final`** [1] |
| no-arg 생성자 | 직접/플러그인으로 가능 | 직접 불가, no-arg 플러그인이 합성 [2] | **합성 불가** (final + backing field 없음) [1][2] |
| `@Embeddable` 매핑 | **가능** (이 저장소 Email/AccountName) | **가능** (BrandName) | **불가** [1][2] |
| `AttributeConverter` 매핑 | 가능 | 가능 | 가능하지만 mangling/nullable 주의 [3][7] |
| boilerplate | 많음 (equals/hashCode/toString 수동) | 적음 | 적음 |
| 주요 주의점 | equals 깜빡 누락 | toString이 값 노출(PII) — 이 저장소는 override | 단일 값 전용, JVM mangling [1] |

> 참고 — value class의 `init` 블록: Kotlin 공식 docs는 value class가 `init` 블록과 secondary 생성자를 가질 수 있다고 명시합니다 ("they are allowed to declare properties and functions, have an `init` block and secondary constructors") [1]. 따라서 검증 로직(invariant)은 value class에서도 작성 가능합니다. 제약은 어디까지나 **backing field 없음 + final**입니다.

---

## 4. "data class 회피"는 왜 `@Entity` 한정인가

JetBrains IntelliJ IDEA 블로그(2026-01)와 JPA Buddy 가이드는 **엔티티에 data class를 쓰지 말라**고 권고하며, 그 근거를 명시적으로 엔티티의 관리 특성에서 끌어옵니다. JetBrains 글은 "However, entities differ because they are managed objects."라고 못 박고, 논의 전체가 엔티티 생명주기 / dirty checking / lazy loading에 집중되어 있습니다 [4]. (직접 확인)

네 가지 이유 (주장 1 — 직접 확인):

**(a) 프록시 생성을 위해 `open`이 필요한데 data class는 `final`** — JPA Buddy: data class는 "final by design and cannot be marked as `open` in Kotlin." 그래서 Hibernate의 프록시 메커니즘이 막히고 ToOne 연관의 lazy loading이 불가해집니다 [5]. JetBrains도 "Must be open (non-final) so the provider can create proxy subclasses"라고 명시 [4].

**(b) JPA는 no-arg 생성자가 필요한데 data class는 생성자 파라미터가 필수** — JetBrains: "Must provide a no-argument constructor, used by the persistence provider" [4]. (단, kotlin-jpa no-arg 플러그인이 이를 합성해 줍니다 — §6.)

**(c) 엔티티는 mutable 필드가 필요(val 불가)** — JetBrains: data class는 "Immutable by default (val properties)"인데 엔티티는 "Must have mutable, non-final attributes so the provider can perform lazy loading as well as detect and persist changes" [4].

**(d) 엔티티 equals/hashCode는 식별자만 써야 하는데 data class는 모든 프로퍼티를 사용** — JetBrains: data class는 "Use all properties"하지만 엔티티는 "Should rely only on type and primary key" [4]. JPA Buddy는 두 가지 구체적 폐해를 듭니다: lazy 필드가 equals/hashCode/toString에 포함되면 "calling them results in unwanted requests to the DB or a LazyInitializationException", 그리고 ID가 생성 시점에 바뀌므로 "once the id is generated (on its first save), the hashCode gets changed" → 컬렉션에서 방금 넣은 엔티티를 못 찾는 문제 [5]. (이 ID-변경 폐해는 별도 글에서도 반복 확인됨 [8].)

**핵심:** (a)(c)는 프록시/dirty-checking, (d)는 식별자 기반 동일성 — **전부 엔티티에만 존재하는 특성**입니다. 두 1차 출처 모두 `@Entity`만 다루며, `@Embeddable`/VO에 data class를 써도 되는지는 **언급하지 않습니다** [4][5]. 따라서 "data class 금지"를 embeddable로 확장하는 것은 과일반화입니다.

---

## 5. `@Embeddable` VO에 data class가 적합한 이유 (주장 2)

§4의 네 폐해를 `@Embeddable` VO에 대입하면 대부분 사라집니다:

- **(a) 프록시 / (c) mutable 필드**: embeddable은 프록시 대상도, lazy 연관의 주체도 아닙니다. 소유 엔티티의 컬럼에 인라인되는 값 묶음이라 `val` 불변이 자연스럽습니다.
- **(d) 식별자 기반 equals**: embeddable VO에는 생성되는 ID가 없습니다. "ID가 persist 후 바뀌어 hashCode가 깨진다"는 폐해 [5][8]가 **구조적으로 발생하지 않습니다.** 오히려 VO는 값 동등성이 본질이므로 모든 프로퍼티 기반 equals/hashCode가 **정확한** 의미를 줍니다.
- **(b) no-arg 생성자**: kotlin-jpa(no-arg) 플러그인이 `@Embeddable`에도 no-arg를 합성합니다 — "The plugin specifies `@Entity`, `@Embeddable`, and `@MappedSuperclass` no-arg annotations automatically" [2]. (직접 확인)

요약하면, data class가 자동 생성하는 값 기반 `equals`/`hashCode`는 엔티티에선 틀린 의미지만 **VO에선 맞는 의미**입니다.

> 출처 강도에 대한 정직한 표기: Kotlin 공식 docs는 "data class가 모든 프로퍼티로 equals/hashCode를 만든다"는 메커니즘과, no-arg 플러그인이 `@Embeddable`을 포함한다는 사실을 **직접 확인**해 줍니다 [1][2]. 다만 "그러므로 @Embeddable VO에는 data class가 권장된다"고 **한 문장으로 단언한 1차 권위 출처(예: Hibernate User Guide / Spring 공식 가이드)는 이번 검증에서 직접 확인하지 못했습니다.** factor10 "Implementing Value Objects in Kotlin"은 **VO에 data class를 직접 권장**합니다(재검증에서 WebFetch 성공)[11]: "you can leverage Kotlin's Data class. It automatically implements the Equals and HashCode methods based on the attributes of the class, **which is exactly what we want**" — VO의 값 동등성과 data class 자동 equals/hashCode가 일치한다는 근거입니다. 다만 이 글은 JPA `@Embeddable`을 다루진 않으므로, "**@Embeddable** VO에 data class"라는 영속 컨텍스트 한정 단언은 여전히 메커니즘([1][2]) + factor10의 VO 논리[11]에서 **연역**으로 보강합니다. 이 저장소는 위 논리(폐해 (a)~(d)가 embeddable에 미적용 + 값 동등성 일치)에 근거해 data class를 채택합니다.

---

## 6. value class를 `@Embeddable`로 못 쓰는 이유 + AttributeConverter 경로

### 6.1 왜 `@Embeddable` 불가인가 (주장 3·4 — Kotlin 공식 docs 직접 확인)

Kotlin 공식 inline value class 문서 [1]:

- **primary 생성자에 프로퍼티 정확히 1개** — "An inline class must have a single property initialized in the primary constructor."
- **backing field 없음, computed property만** — "Inline class properties cannot have backing fields. They can only have simple computable properties (no `lateinit`/delegated properties)."
- **항상 final** — "inline classes cannot extend other classes and are always `final`."

Hibernate가 `@Embeddable`을 인스턴스화하려면 (no-arg 생성자로) 빈 인스턴스를 만든 뒤 backing field/프로퍼티에 컬럼 값을 채워 넣어야 합니다. value class는 ① final + backing field 없음이라 no-arg 인스턴스화 + 필드 주입 모델이 성립하지 않고, ② no-arg 플러그인도 no-arg를 **합성해 줄 수 없습니다.** no-arg 플러그인 문서는 그 대상을 "**classes** with a specific annotation"(`@Entity`/`@Embeddable`/`@MappedSuperclass`)이라고만 명시하며 [2], value class는 위 final/backing-field 제약 때문에 합성 자체가 불가능합니다. → **value class는 `@Embeddable`로 매핑 불가** (주장 4).

> 정밀 표기: "no-arg 플러그인이 value class를 명시적으로 제외한다"는 문장이 docs에 따로 있는 것은 아닙니다 [2]. 불가의 근거는 value class의 final + backing-field-없음 제약 [1]에서 **연역**됩니다. 즉 "플러그인이 value class엔 적용 불가"는 **메커니즘 추론**이며, "@Embeddable/@Entity/@MappedSuperclass class에만 no-arg를 합성한다"는 부분만 docs 직접 확인입니다 [2].

### 6.2 no-arg 합성 생성자의 성격 (주장 6 — 직접 확인)

no-arg 플러그인이 만드는 생성자는 **synthetic**이라 Java/Kotlin에서 직접 호출 불가, reflection으로만 호출됩니다 — "The generated constructor is synthetic, so it can't be directly called from Java or Kotlin, but it can be called using reflection." [2] (이것이 Hibernate가 reflection으로 인스턴스화하는 전제와 맞물립니다.)

### 6.3 AttributeConverter 경로 — 가능성과 한계 (주장 5)

value class는 `@Embeddable`은 못 쓰지만, **엔티티의 직접 필드 + `AttributeConverter`(또는 Hibernate 6 basic 타입 매핑)** 로 단일 컬럼에 영속화할 수 있습니다.

- `AttributeConverter`는 `convertToDatabaseColumn` / `convertToEntityAttribute` 두 메서드로 **VO ↔ 단일 컬럼** 변환을 정의합니다. converter는 null을 받을 수 있어 명시적 null 체크가 필요합니다 — Hibernate User Guide 6.6, "AttributeConverters" 절에서 시그니처와 null 예시 확인 [6]. (직접 확인)
- **JVM name mangling**: value class를 쓰면 getter 함수명이 hashcode 접미사로 mangle됩니다 — Kotlin docs: "functions using inline classes are mangled by adding some stable hashcode to the function name" [1], Xebia: 프로퍼티 이름이 "include a `name-hashcode`" [3]. (직접 확인)
- **Hibernate가 mangled name을 unmangle하는가**: 검색 종합 결과 "Hibernate detects and unmangles mangled names to create column names without the mangling suffix"라는 진술이 있으나 [7], 이를 명시한 **1차 원문(Hibernate 공식 docs/이슈)을 직접 fetch해 확인하지는 못했습니다. (보강 검증 필요)** 직접 fetch한 Xebia 글은 mangling 발생만 다루고 Hibernate의 unmangle 여부는 다루지 않았습니다 [3].
- **nullable value class 엣지케이스**: nullable한 value class 프로퍼티는 기본 동작에 의존하지 말고 converter를 명시해야 한다고 보고됩니다 — Xebia: "It should work by default unless you need to introduce a nullable property of type Value Class. In this case, you need to ensure that you introduce a converter for the nullable type." [3] (직접 확인). Spring Framework에도 value class 프로퍼티 처리 관련 이슈(SpEL)가 별도로 존재해 [10], 프레임워크 경계에서 value class 처리가 버전별로 균일하지 않음을 시사합니다 **(이슈 본문 직접 fetch 미수행 — 보강 검증 권장)**.

**정리:** value class + AttributeConverter는 "단일 값 + 도메인 순수 래퍼 + 컬럼 1개"에 한해 합리적이지만, mangling/nullable 처리에서 버전별 주의가 필요합니다. 다중 컬럼 VO나 검증 invariant 위주라면 `@Embeddable data class`가 더 단순하고 안전합니다.

---

## 7. 이 프로젝트 권장안

### 7.1 기본: `@Embeddable data class` (현행 BrandName)

이 저장소 `apps/commerce-api/.../domain/brand/BrandName.kt`:

```kotlin
package com.loopers.domain.brand

import com.loopers.support.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class BrandName(
    @Column(name = "name", nullable = false, length = 50)
    val value: String,
) {
    init {
        // 컬럼 상한을 @Column.length와 init 양쪽에 직접 숫자로 둠 (fail-fast invariant)
        if (value.isBlank() || value.length > 50) {
            throw BadRequestException(BrandErrorCode.INVALID_BRAND_NAME)
        }
    }

    // 프로젝트 VO 규칙: toString은 원문 value 그대로 (data class 기본 "BrandName(value=..)" 대신)
    override fun toString(): String = value
}
```

근거:
- VO의 값 동등성이 data class 자동 `equals`/`hashCode`와 일치 (§5).
- `@Embeddable`이므로 §4의 엔티티 폐해 (a)(c)(d) 미적용, (b)는 no-arg 플러그인이 해결 [2].
- `init`에서 길이 + 형식 검증으로 도메인 invariant를 fail-fast 강제 — 컬럼 상한 초과가 DB까지 가서 500으로 떨어지지 않도록 (CLAUDE.md §4 VO 검증 패턴).
- `toString()`은 원문 반환하되, **앱 코드에서 VO를 직접 문자열 보간/로깅하지 말 것** — PII 노출 우회 위험 (CLAUDE.md §4).

> 일반 class vs data class: 기존 `Email`/`AccountName`은 일반 class + 수동 equals/hashCode입니다. data class로 가면 boilerplate가 줄지만, **toString 오버라이드는 유지**해야 합니다(기본 toString이 값을 노출). 둘 다 유효하며, 신규 VO는 BrandName처럼 data class를 기본으로 합니다.

### 7.2 value class + AttributeConverter를 고려할 때

다음을 **모두** 만족할 때만:
- 컬럼이 **1개**이고,
- 도메인 래퍼의 **제로 오버헤드**(인라인)나 강타입 ID(예: `BrandId`) 같은 순수성이 중요하고,
- nullable이 아니거나, nullable이면 전용 converter를 명시할 의향이 있을 때 [3].

이 경우 VO는 엔티티의 직접 필드로 두고 `@Convert`로 converter를 연결합니다. 다중 컬럼이거나 검증 invariant 중심이면 `@Embeddable data class`가 우선입니다.

---

## 8. 주장별 검증 결과 요약

| # | 주장 | 결과 | 근거 |
|---|---|---|---|
| 1 | "data class 회피"는 @Entity 한정 (4가지 이유) | **확인** | [4][5] 모두 @Entity만 다룸, 4이유 명시 |
| 2 | @Embeddable VO엔 data class 적합 | **확인(VO 일반) / 연역(@Embeddable 한정)** | "VO엔 data class 권장(값 동등성)"은 factor10 직접 확인[11]. "@Embeddable 특정" 단언 원문은 없어 메커니즘([1][2])+VO논리[11] 연역 |
| 3 | value class = 프로퍼티 1개 + backing field 없음 + final | **확인** | Kotlin 공식 docs [1] |
| 4 | value class는 @Embeddable 매핑 불가 | **확인(연역)** | [1]의 제약 + [2]의 no-arg 대상 범위로 연역 |
| 5 | value class는 AttributeConverter로 단일 컬럼 영속화 가능, 단 엣지케이스 | **부분 확인** | converter 시그니처/null [6], mangling [1][3], nullable converter [3]. "Hibernate unmangle"은 미확인 [7] |
| 6 | no-arg 합성 생성자는 synthetic, reflection만 호출 | **확인** | Kotlin 공식 docs [2] |

**반증/주의된 것:** 단정적으로 반증된 주장은 없습니다. 다만 (주장 5의) "Hibernate가 mangled name을 unmangle한다"는 1차 출처 직접 확인 실패 — 검색 종합 [7]에만 존재 → **미확인**. (주장 2의) "@Embeddable VO에 data class 권장"을 한 문장으로 단언한 1차 권위 출처도 직접 확인 실패 → 메커니즘 기반 **연역으로 보강**.

---

## 9. 출처 목록

[1] Kotlin 공식 — Inline value classes: <https://kotlinlang.org/docs/inline-classes.html> (직접 확인 — 단일 프로퍼티/backing field 없음/final/mangling/init 블록)
[2] Kotlin 공식 — No-arg compiler plugin: <https://kotlinlang.org/docs/no-arg-plugin.html> (직접 확인 — kotlin-jpa가 @Entity/@Embeddable/@MappedSuperclass에 no-arg 합성, synthetic·reflection-only)
[3] Xebia — Kotlin Gems: Features I Wish I Discovered Sooner: <https://xebia.com/blog/kotlin-gems-features-i-wish-i-discovered-sooner/> (직접 확인 — value class mangling, nullable value class는 converter 필요)
[4] JetBrains IntelliJ IDEA Blog (2026-01) — How to Avoid Common Pitfalls With JPA and Kotlin: <https://blog.jetbrains.com/idea/2026/01/how-to-avoid-common-pitfalls-with-jpa-and-kotlin/> (직접 확인 — @Entity 한정, data class 4가지 폐해)
[5] JPA Buddy — Best Practices and Common Pitfalls of Using JPA (Hibernate) with Kotlin: <https://jpa-buddy.com/blog/best-practices-and-common-pitfalls/> (직접 확인 — final/proxy, equals·hashCode all-fields, ID 변경 폐해)
[6] Hibernate ORM 6.6 User Guide — Embeddable values / AttributeConverters 절: <https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html> (직접 확인 — AttributeConverter 시그니처·null 처리, @Instantiator)
[7] WebSearch 종합 (Hibernate value class column name unmangling) — **1차 원문 직접 fetch 미수행, 검색 요약 기반** (보강 검증 필요)
[8] Paul Newport (Medium) — Kotlin data classes for JPA equals/hashCode 변화: <https://medium.com/@newportmeister/you-have-to-be-careful-about-using-kotlin-data-classes-for-jpa-as-the-auto-generated-equals-and-3fddde290dcb> (검색 종합에서 ID 변경 폐해 재확인 — 원문 직접 fetch 미수행)
[9] WebSearch 종합 (data class @Embeddable value object) — "@Embeddable에서는 @Entity보다 data class가 더 무난, 단 equals/hashCode 의미 검토 필요" — **검색 요약 기반** (보강 검증 권장)
[10] Spring Framework Issue #30468 — Support Kotlin value class properties in SpEL: <https://github.com/spring-projects/spring-framework/issues/30468> (이슈 존재 확인, 본문 직접 fetch 미수행 — 프레임워크 경계 value class 처리 불균일성 시사)
[11] factor10 — Implementing Value Objects in Kotlin: <https://www.factor10.com/news/implementing-value-objects-in-kotlin/> (**재검증 WebFetch 직접 확인** — "leverage Kotlin's Data class ... automatically implements the Equals and HashCode ... which is exactly what we want", data class와 value class를 상보적으로 제시. JPA/@Embeddable은 미언급)

### 직접 fetch를 시도했으나 권한/리다이렉트로 확인 못 한 출처 (보강 검증 대상)
- kt.academy — Effective Kotlin Item 42 (equals): <https://kt.academy/article/ek-equals> (WebFetch 권한 거부)
- Vlad Mihalcea — business key/natural id equals: <https://vladmihalcea.com/the-best-way-to-map-a-naturalid-business-key-with-jpa-and-hibernate/> (WebFetch 권한 거부)
- Valentin Goncharov (Medium) — data classes and JPA/Hibernate: <https://medium.com/@goncharov.valentin/a-practical-point-of-view-on-kotlin-data-classes-and-jpa-hibernate-c69370b975e1> (WebFetch 권한 거부)
- Baeldung — Working with Kotlin and JPA: <https://www.baeldung.com/kotlin/jpa> (HTTP 403)

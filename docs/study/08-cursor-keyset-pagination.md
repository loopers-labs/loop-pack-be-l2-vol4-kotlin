# Cursor(Keyset) 기반 페이지네이션 — 기술 레퍼런스

> 대상 환경: Kotlin 2.0.20 / Spring Boot 3.4.4 → Spring Data JPA 3.4.x, Spring Data Commons 3.4.x, Hibernate ORM 6.6.x / Java 21.
> Boot 3.4.x가 관리하는 실제 버전: `spring-data-jpa` / `spring-data-commons` 3.4.13, `hibernate-core` 6.6.x [7].
>
> 표기 규칙: 각 사실 주장 뒤의 `[n]`은 문서 말미 출처 목록의 번호입니다.
> "직접 확인" = 원문을 WebFetch로 가져와 확인. "(출처 미확인)" = 1차 출처 fetch가 권한/네트워크로 막혀 추가 검증이 필요한 주장.
>
> 검증 한계 고지: API 사양·버전·동작 정의는 모두 `docs.spring.io` 공식 문서에서 직접 확인했습니다. **offset의 중복·누락 현상은 use-the-index-luke "No Offset" 원문으로 직접 확인 완료(승격)** [11]. Vlad Mihalcea / Baeldung은 여전히 WebFetch 권한 제약(403/거부)으로 직접 확인하지 못해, 이들에만 의존하는 주장은 "(출처 미확인)"으로 남겨둡니다.

---

## 1. 한 줄 결론 + offset vs keyset 비교표

**무한 스크롤/대용량 목록처럼 "다음 N개"만 순차로 소비하는 화면에는 offset 대신 cursor(keyset/seek) 페이지네이션을 씁니다.** offset은 깊은 페이지에서 DB가 전체 결과를 만들고(materialize) 앞부분을 건너뛰어야 하지만 [3], keyset은 인덱스 위치에서 바로 작은 결과만 구성하므로 컴퓨팅/IO 부담이 작습니다 [3]. 단, keyset이 정확히 동작하려면 정렬 키가 **결정적(deterministic) total order**여야 하고, 이를 위해 비유니크 정렬 키 뒤에 유니크 키(보통 `id`)를 타이브레이크로 붙여야 합니다 [1].

| 항목 | Offset 페이지네이션 | Keyset(Cursor/Seek) 페이지네이션 |
|---|---|---|
| 위치 지정 방식 | "앞에서 N개 건너뛰기" (offset counter) [3] | 마지막으로 본 행의 정렬 키 값(keyset)을 WHERE에 넣어 그 이후부터 [1] |
| 깊은 페이지 비용 | 대부분 DB가 전체 결과를 materialize한 뒤 offset까지 skip — 추가 부하 [3] | keyset 위치에서 훨씬 작은 결과만 구성, 전체 materialize 불필요 [3] |
| 인덱스 활용 | 정렬 인덱스가 있어도 skip 구간을 스캔 (출처 미확인) | 정렬 필드 인덱스와 맞으면 시작점 위치를 인덱스로 탐색 [1] |
| 임의 페이지 점프("100페이지로") | 가능 (offset 계산) | 불가 — 순차 forward/backward 위주 [1] |
| 데이터 삽입/삭제 중 중복·누락 | 페이지 사이 행 삽입 시 **중복**, 삭제 시 누락 등 drift 발생[11] | 마지막 본 행의 키 기준 필터라 동시 삽입/삭제에도 drift 없음[11] |
| Spring Data API | `OffsetScrollPosition`, `Pageable`/`Page` | `KeysetScrollPosition`, `Window<T>` [1][4] |
| 정렬 키 요건 | 안정 정렬 권장 | **non-null + 결정적 total order(유니크 타이브레이크) 필수** [1] |

> offset의 중복·누락(drift)은 use-the-index-luke "No Offset" 원문으로 확인했습니다[11]: "you'll get duplicates in case there were new rows inserted between fetching two pages", "The idea to use the number of rows seen to skip over them later is simply wrong", keyset은 "solves the problem of drifting results ... even faster than offset". 공식 Spring 문서[3]는 offset의 "전체 결과 materialize 후 skip" 부하만 명시하고 drift는 다루지 않으므로, drift 근거는 [11]로 보강합니다. (남은 "(출처 미확인)" 행 = "정렬 인덱스 skip 구간 스캔" 세부는 [11]/[3]가 직접 단정하지 않아 유지.)

---

## 2. Keyset 원리 — 정렬 키와 타이브레이크 (직접 확인)

Spring Data 공식 문서의 정의 [3]:

- **Offset-based scrolling**: "Offset scrolling ... uses an Offset counter to skip a number of results and let the data source only return results beginning at the given Offset." 클라이언트에는 일부만 가지만, "most databases require materializing the full query result before your server can return the results" — 즉 서버는 전체 결과를 만들어야 합니다 [3].
- **Keyset-Filtering**: "approaches result subset retrieval by leveraging built-in capabilities of your database ... This approach maintains a set of keys to resume scrolling by passing keys into the query, effectively amending your filter criteria." 그리고 "The database needs only constructing a much smaller result from the given keyset position without the need to fully materialize a large result and then skipping results until reaching a particular offset." [3]

핵심은 "마지막으로 본 행의 정렬 키 값을 다음 쿼리의 필터 조건으로 추가"한다는 것입니다 [1][3]. 그래서 정렬 키에 다음 두 제약이 붙습니다.

### 2.1 결정적 total order (유니크 타이브레이크) — 필수

Spring Data 문서는 keyset이 **유니크한 결과를 보장하도록 정렬 순서에 기본키(또는 복합 기본키의 나머지)를 덧붙인다**고 명시합니다 [1]:

> "amends your sort order by including the primary key (or any remainder of composite primary keys) to ensure each query result is unique." [1]

즉 `price` 같은 비유니크 컬럼만으로 정렬하면 같은 `price` 행들 사이 순서가 비결정적이라, 그 값으로 keyset 필터를 걸 때 경계가 모호해집니다. 그래서 정렬 끝에 유니크 컬럼(여기서는 `id`)을 타이브레이크로 둬야 total order가 성립합니다 [1].

### 2.2 정렬 필드 non-null + 결과에 포함

Spring Data 문서 [1]:

- **non-null 필수**: "Keyset-Filtering requires the keyset properties (those used for sorting) to be non-nullable. This limitation applies due to the store specific `null` value handling of comparison operators." [1]
- **정렬 프로퍼티가 쿼리 결과에 매핑되어야 함**: "Scroll queries applying Keyset-Filtering require the properties used in the sort order to be returned by the query, and these must be mapped in the returned entity." projection 사용 시에도 "make sure to include all properties that you've sorted by to avoid keyset extraction failures." [1]

이유: 다음 페이지의 keyset을 만들려면 마지막 행에서 **정렬에 쓴 모든 값**을 꺼내야 하기 때문입니다. 정렬 키가 결과에 없으면 keyset 추출이 실패합니다 [1].

---

## 3. Spring Data Window / ScrollPosition API + 버전 (직접 확인)

### 3.1 버전

- `org.springframework.data.domain.ScrollPosition`는 **`@since 3.1`** 입니다 (Spring Data Commons) [5][6]. `keyset()` / `offset()` / `forward(Map)` / `backward(Map)` / `of(Map, Direction)` 정적 팩토리 모두 같은 타입에 정의되어 있습니다 [5][6].
- 이 저장소의 Boot 3.4.4는 Spring Data 3.4.x를 끌어오므로(실측 3.4.13) [7], `@since 3.1` API는 안전하게 사용 가능합니다. 즉 "Spring Data 3.1+ GA, Boot 3.2+/3.4" 가정은 성립합니다 [5][7].
- 단, Spring Boot가 별도 `spring-data-bom` 좌표를 노출하기보다 개별 모듈(`spring-data-jpa`, `spring-data-commons`)을 직접 3.4.x로 핀했습니다 [7]. ("BOM 버전 한 줄로 매핑"이라는 표현은 부정확 — 개별 모듈 핀이 정확)

### 3.2 핵심 타입

**`Window<T>`** — scroll 쿼리의 반환 타입 [3][4]. 주요 메서드 [4]:

- `positionAt(int index)` — 특정 인덱스 요소의 scroll position을 얻어 다음 `Window<T>`를 가져옴 [4]
- `isLast()`, `isEmpty()`, `size()`, `getContent()` [4]
- (이 저장소 매핑 관점) `Window`는 "다음이 있는지"와 "각 요소의 위치"를 알려주는 컨테이너이며, 우리 도메인 포트는 이를 `CursorPage`로 변환합니다 (§5).

**`ScrollPosition`** — 요소의 정확한 위치를 식별하는 추상 베이스. 위치 파라미터는 **exclusive**(주어진 위치 *다음*부터 결과 시작) [4]. 정적 팩토리 [5][6]:

```java
ScrollPosition.keyset()                                   // KeysetScrollPosition (초기 위치)
ScrollPosition.offset()                                   // OffsetScrollPosition (초기 위치)
ScrollPosition.offset(long offset)                        // OffsetScrollPosition
ScrollPosition.forward(Map<String,?> keys)                // KeysetScrollPosition (forward)
ScrollPosition.backward(Map<String,?> keys)               // KeysetScrollPosition (backward)
ScrollPosition.of(Map<String,?> keys, Direction dir)      // KeysetScrollPosition
```

> 주의: 과제 컨텍스트에 적힌 `ScrollPosition.of(keys, FORWARD)`는 공식 API와 일치합니다 — `of(Map, Direction)`가 실제로 존재하며 `KeysetScrollPosition`을 반환합니다 [5]. 단 `forward(Map)`/`backward(Map)`/`of(Map, Direction)`는 `KeysetScrollPosition`가 아니라 **`ScrollPosition`(상위 인터페이스)의 static 메서드**입니다 [5]. `KeysetScrollPosition` 자체에는 keyset을 받는 static 팩토리가 없고, 인스턴스 메서드 `getKeys()`, `getDirection()`, `scrollsForward()`/`scrollsBackward()`, `forward()`/`backward()`(인자 없음), `reverse()`, `isInitial()`만 있습니다 [6].

**`KeysetScrollPosition` / `OffsetScrollPosition`** — keyset/offset 구체 구현 [3][4]. `ScrollPosition.Direction`은 중첩 enum으로 `FORWARD` / `BACKWARD` 값을 가집니다 [6].

**리포지토리 메서드 형태** [3][4]:

```java
interface UserRepository extends Repository<User, Long> {
  Window<User> findFirst10ByLastnameOrderByFirstname(String lastname, ScrollPosition position);
  // 또는 KeysetScrollPosition / OffsetScrollPosition 파라미터로 좁힐 수도 있음 [4]
}
```

메서드는 `Sort`(요청별 동적 정렬)와 `Limit`(요청별 동적 개수 제한)도 파라미터로 받을 수 있습니다 [4]. keyset은 정렬 필드와 일치하는 인덱스가 있을 때 가장 효과적이라 정적 정렬이 잘 맞습니다 [1].

**`WindowIterator<T>`** — 반복 시 조건 분기를 없애주는 유틸 [4]:

```java
WindowIterator<User> users = WindowIterator.of(position ->
    repository.findFirst10ByLastnameOrderByFirstname("Doe", position))
  .startingAt(ScrollPosition.keyset());        // 처음부터 시작

while (users.hasNext()) {
  User u = users.next();
}
```

수동 반복 패턴(문서 예시) [4]:

```java
Window<User> users = repository.findFirst10ByLastnameOrderByFirstname("Doe", ScrollPosition.offset());
do {
  for (User u : users) { /* consume */ }
  if (users.isLast() || users.isEmpty()) break;
  users = repository.findFirst10ByLastnameOrderByFirstname("Doe", users.positionAt(users.size() - 1));
} while (!users.isEmpty());
```

---

## 4. 커서 표현 설계

### 4.1 우리가 택한 모델

```text
CursorPage<T>(content: List<T>, hasNext: Boolean, nextCursor: Cursor?)
Cursor                       // base interface
 ├─ IdCursor(id)             // latest 단일 키, 공용
 └─ (Product) 복합 cursor    // price_asc / likes_desc 용, 상속 추가 예정
```

- `CursorPage<T>`: domain/shared의 결과 래퍼. `content` + "다음이 있는가(`hasNext`)" + "다음 시작점(`nextCursor`, 없으면 null)".
- `Cursor`: base **interface**. 정렬별로 구현체를 상속한다. `IdCursor(id)`는 `latest`(= `id DESC`)용 단일 키 커서이며 공용.
- Product는 `price_asc`/`likes_desc`용 복합 커서를 별도 구현체로 추가한다.

### 4.2 대안과 트레이드오프

| 대안 | 장점 | 단점 | 판단 |
|---|---|---|---|
| **interface + 정렬별 구현체** (택함) | 정렬마다 커서 형태가 타입으로 드러남, 컴파일 타임 안전, 모듈 분리 시 확장 용이 | 정렬 종류만큼 클래스 증가 | 정렬별 커서 키가 실제로 다르므로(§4.3) 타입 분기가 자연스러움 |
| `sealed interface` | when 망라성 체크 | 같은 모듈 제약 — 모듈 분리(Product가 별도 모듈로 갈 가능성)와 충돌 | 모듈 분리 대비해 sealed 대신 일반 interface 선택 (의도된 결정) |
| `Cursor = Map<String, Any>` | Spring `ScrollPosition.of(keys, ...)`와 1:1, 새 정렬에 클래스 불필요 | 키 이름 오타/타입 런타임 검증, 도메인 의미가 사라짐, presentation 인코딩 시 스키마 불명확 | 도메인 표현은 타입 안전을 택함. Map은 어댑터 내부에서 Spring API로 넘길 때만 사용 |
| 제네릭 `Cursor<K>` | 키 타입 파라미터화 | 복합 키(2개 이상)에서 `K`가 튜플이 되어 복잡, 이득 대비 과함 | YAGNI — 단일/복합을 구현체로 나누는 편이 단순 |

> Spring의 keyset 키는 결국 `Map<String,?>`로 표현됩니다 [5]. 우리 `Cursor` 구현체 → `Map` 변환은 **어댑터의 책임**으로 한정해, 도메인은 의미 있는 타입을 유지하고 Map의 stringly-typed 단점은 경계 한 곳에 가둡니다.

### 4.3 nextCursor와 인코딩 레이어 경계

- `nextCursor`는 **raw 타입 커서**(`IdCursor` 등)로만 노출합니다. base64 opaque 인코딩은 **presentation(controller) 책임**이며 이 설계 범위 밖입니다.
- 이유: 도메인/어댑터는 "다음 시작점이 무엇인가"라는 의미만 다루고, "그것을 클라이언트에 어떻게 직렬화/난독화해 보낼 것인가"는 표현 계층 정책입니다. 인코딩을 도메인에 두면 표현 정책이 도메인으로 누수됩니다.
- 복합 커서(§4.4)일수록 opaque 인코딩 이점이 큽니다 — 여러 키를 한 문자열로 묶어 클라이언트가 내부 구조에 의존하지 못하게 합니다. (이 인코딩 구현은 범위 밖)

### 4.4 복합 키 정렬에서 커서가 깨지는 이유 (직접 확인 기반)

`price ASC, id DESC` 같은 복합 정렬에서는 **마지막 행의 `price`와 `id` 두 값 모두**가 다음 keyset에 들어가야 합니다 [1]. `id` 하나만으로 커서를 만들면 같은 `price` 그룹 내 경계를 표현할 수 없어 forward 필터가 깨집니다. 그래서 정렬별로 커서가 담는 키 집합이 달라지고(§4.1의 정렬별 구현체), 단일 `IdCursor`로는 복합 정렬을 표현할 수 없습니다. 이는 §2.1의 "정렬에 쓴 모든 프로퍼티가 keyset에 포함/결과에 매핑되어야 한다"는 공식 요건과 같은 이야기입니다 [1].

---

## 5. 우리 구현과 이유

### 5.1 레이어 매핑

- **domain 포트**는 `CursorPage<T>`를 반환합니다. domain은 Spring Data의 `Window`/`ScrollPosition`을 모릅니다 (아키텍처 경계 보존).
- **infrastructure 어댑터**가 Spring Data keyset scrolling을 수행합니다: `ScrollPosition.keyset()`(초기) 또는 `ScrollPosition.of(keys, FORWARD)`(이어보기) [5] → 리포지토리가 `Window<T>` 반환 [3][4] → 어댑터가 `Window → CursorPage` 매핑(`hasNext = !window.isLast()` 류, `nextCursor`는 마지막 요소의 정렬 키로 구성) [4].
- `Limit` / `Sort`는 어댑터에서 정렬 키 규칙(§5.2)에 따라 구성합니다 [4].

이 분리의 이유: `Window`/`ScrollPosition`은 Spring Data 인프라 타입이라, domain이 직접 의존하면 영속 기술이 도메인으로 누수됩니다. `CursorPage`/`Cursor`는 순수 도메인 표현으로 두고, 변환을 어댑터 한 곳에 가둡니다.

### 5.2 정렬 키 규칙 (타이브레이크)

비유니크 1차 키 뒤에 항상 `id DESC`를 타이브레이크로 둡니다:

| 정렬 | 정렬 키 | 커서 |
|---|---|---|
| `latest` | `id DESC` | `IdCursor(id)` — 1차 키가 이미 유니크 |
| `price_asc` | `price ASC, id DESC` | 복합 커서(price, id) |
| `likes_desc` | `likeCount DESC, id DESC` | 복합 커서(likeCount, id) |

근거: keyset이 중복·누락 없는 결정적 결과를 내려면 정렬이 total order여야 하고, Spring Data도 유니크 보장을 위해 PK를 정렬에 덧붙인다고 명시합니다 [1]. 우리는 그 PK 타이브레이크를 **명시적으로** 정렬 키 규칙에 박아, `price`/`likeCount`가 같은 행들 사이에서도 순서가 고정되게 합니다 [1].

### 5.3 `IdCursor` 공용 + 정렬별 상속 + 어댑터 한정의 이유

- **`IdCursor` 공용**: `latest`처럼 단일 유니크 키로 충분한 정렬은 어느 도메인에서나 같은 형태라 공용 구현체로 둡니다 (중복 제거).
- **정렬별 상속**: `price_asc`/`likes_desc`는 키 집합이 달라(§4.4) 별도 구현체가 필요합니다. interface 상속으로 정렬↔커서 형태를 타입에 묶습니다.
- **변환 어댑터 한정**: `Cursor ↔ Map<String,?>`/`Window ↔ CursorPage` 변환을 어댑터에만 두어, Spring Data 의존을 인프라에 가두고 domain 순수성을 지킵니다.

> 이 구현 항목들은 우리 설계 결정이며 외부 출처가 아닌 §2~§4의 공식 사양 위에서 도출했습니다. "왜 타이브레이크가 필요한가"·"왜 정렬 키가 결과에 있어야 하는가"의 근거만 [1]에 있습니다.

---

## 6. 출처 목록

> [1][3][4]는 동일한 Spring Data 공식 "Scrolling" 문서의 서로 다른 섹션(요건 / offset·keyset 정의 비교 / API)입니다. fetch 시점 페이지 헤더는 Commons 4.0.5 빌드로 표기됐으나, 본문에 인용한 `ScrollPosition`/`Window`/keyset 요건 API는 `@since 3.1`로 3.4.x에 동일하게 존재합니다 [5][6][7].

- [1] Scrolling — Keyset-Filtering 요건(유니크 PK 덧붙임, non-null, 정렬 프로퍼티 결과 포함). Spring Data 공식 문서. 직접 확인. https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/scrolling.html
- [2] (예약 — 본문 미사용)
- [3] Scrolling — Offset-based vs Keyset-Filtering 정의·성능(전체 materialize 후 skip vs 작은 결과 구성). Spring Data 공식 문서. 직접 확인. https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/scrolling.html
- [4] Scrolling — `Window<T>`(`positionAt`/`isLast`/`isEmpty`/`size`), `WindowIterator`, 리포지토리 메서드 시그니처, `Sort`/`Limit` 지원. Spring Data 공식 문서. 직접 확인. https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/scrolling.html
- [5] `ScrollPosition` API — `keyset()`/`offset()`/`offset(long)`/`forward(Map)`/`backward(Map)`/`of(Map, Direction)`, `@since 3.1`. Spring Data Core Javadoc. 직접 확인. https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/ScrollPosition.html
- [6] `KeysetScrollPosition` API — `getKeys()`/`getDirection()`/`scrollsForward()`/`scrollsBackward()`/`forward()`/`backward()`/`reverse()`/`isInitial()`, `Direction` enum(FORWARD/BACKWARD). Spring Data Core Javadoc. 직접 확인. https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/KeysetScrollPosition.html
- [7] Spring Boot 3.4 Managed Dependency Coordinates — `spring-data-jpa`/`spring-data-commons` 3.4.13, `hibernate-core` 6.6.x. Spring Boot 공식 문서. 직접 확인. https://docs.spring.io/spring-boot/3.4/appendix/dependency-versions/coordinates.html
- [8] Vlad Mihalcea, "SQL Seek Method / Keyset Pagination" — offset 깊은 페이지 비효율·seek 원리. (출처 미확인: 이 환경에서 WebFetch 권한 거부로 원문 직접 확인 실패) https://vladmihalcea.com/sql-seek-keyset-pagination/
- [9] Vlad Mihalcea, "Keyset Pagination with JPA and Hibernate". (출처 미확인: WebFetch 거부) https://vladmihalcea.com/keyset-pagination-jpa-hibernate/
- [10] Vlad Mihalcea, "Keyset Pagination with Spring Data WindowIterator". (출처 미확인: WebFetch 거부) https://vladmihalcea.com/spring-data-windowiterator/
- [11] Markus Winand, use-the-index-luke "No Offset" — "duplicates in case there were new rows inserted between fetching two pages", "The idea to use the number of rows seen to skip over them later is simply wrong", keyset "solves the problem of drifting results ... even faster than offset". **WebFetch 직접 확인(승격).** https://use-the-index-luke.com/no-offset
- [12] Baeldung, "Scroll API in Spring Data JPA". (출처 미확인: HTTP 403으로 fetch 실패) https://www.baeldung.com/spring-data-jpa-scroll-api

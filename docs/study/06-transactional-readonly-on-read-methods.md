# 조회(read) 메서드에 `@Transactional(readOnly = true)`를 붙일지

> 대상 스택: Kotlin 2.0.20, Spring Boot 3.4.4 (→ Spring Data JPA 3.4 / Hibernate 6.6), Java 21
> 작성 목적: `BrandService`의 `get`/`list` read 메서드에서 `@Transactional(readOnly = true)`를 제거하고 write(`register`/`update`/`delete`)만 `@Transactional`을 유지하기로 한 결정의 기술적 근거 정리.

---

## ① 한 줄 결론 + 의사결정 표

**한 줄 결론:** 단일 쿼리로 끝나고 매핑 시점에 lazy association을 건드리지 않는 read 메서드는 service 레벨 `@Transactional(readOnly = true)`가 사실상 redundant합니다. Spring Data JPA repository 메서드 자체가 이미 read 트랜잭션 경계를 제공하기 때문입니다. 단, 아래 표의 조건 중 하나라도 해당하면 service 레벨 read 트랜잭션이 의미를 가집니다.

| 상황 | 권장 | 이유 |
|---|---|---|
| 단일 쿼리 read (예: `get`, 단순 `list`) + eager/basic 필드만 매핑 | service 레벨 트랜잭션 **불필요** | repository 메서드가 이미 자체 read 트랜잭션을 갖고[2], 매핑이 영속성 컨텍스트 밖에서 가능하므로 추가 경계가 redundant |
| 여러 쿼리를 한 트랜잭션/스냅샷·한 커넥션으로 묶어야 할 때 | `@Transactional(readOnly = true)` **권장** | 여러 repository 호출을 하나의 일관된 read 경계로 묶음. read 동안 우발적 flush 차단[1][4] |
| managed entity를 읽고 같은 트랜잭션에서 우발적 write/flush를 막고 싶을 때 | `@Transactional(readOnly = true)` **권장** | Spring이 FlushMode를 MANUAL로 두어 auto-flush/dirty-checking flush를 건너뜀(Spring `HibernateJpaDialect` 소스 + Hibernate javadoc 확인[9][10]) |
| read replica(read-only endpoint)로 라우팅하고 싶을 때 | `@Transactional(readOnly = true)` **권장(드라이버 의존)** | `Connection.setReadOnly(true)`는 "드라이버에 대한 hint"이며[5] 일부 드라이버(예: Aurora MySQL replication URL)가 이를 보고 read endpoint로 라우팅(WebFetch 차단 — [6] 검색 요약 기반) |
| 매핑/뷰 렌더링에서 lazy association 접근이 필요할 때 | 트랜잭션 경계 **필요** (또는 fetch join/`@EntityGraph`) | OSIV가 꺼져 있으면(우리 프로젝트가 그러함) 트랜잭션 밖 lazy 접근은 `LazyInitializationException`[8] |

---

## ② `@Transactional(readOnly = true)`가 실제로 하는 일

### 2-1. 의미: 강제(enforcement)가 아니라 hint

Spring `@Transactional`의 `readOnly` 속성에 대한 공식 Javadoc 정의(직접 fetch 확인[3]):

> "A boolean flag that can be set to `true` if the transaction is effectively read-only, allowing for corresponding optimizations at runtime. Defaults to `false`. This just serves as a **hint for the actual transaction subsystem**; it will *not necessarily* cause failure of write access attempts. A transaction manager which cannot interpret the read-only hint will *not* throw an exception when asked for a read-only transaction but rather **silently ignore the hint**."

즉 `readOnly = true`는 런타임 최적화를 위한 hint이며, 트랜잭션 매니저/드라이버가 해석하지 못하면 조용히 무시될 수 있습니다.

### 2-2. Hibernate 레벨 효과: FlushMode → MANUAL (1차 소스 확인)

Spring의 `HibernateJpaDialect.beginTransaction`은 트랜잭션이 readOnly일 때 현재 Session의 flush mode를 `FlushMode.MANUAL`로 바꾸고, 트랜잭션 종료 시(`cleanupTransaction`/reset) 이전 flush mode로 복원합니다. 소스 원문 직접 확인[9]:

> ```java
> if (readOnly) {
>     // We should suppress flushing for a read-only transaction.
>     if (!flushMode.equals(FlushMode.MANUAL)) {
>         session.setHibernateFlushMode(FlushMode.MANUAL);
>         return flushMode;
>     }
> }
> ```

JDBC 커넥션의 read-only 플래그는 이 클래스가 직접 호출하지 않고 `DataSourceUtils.prepareConnectionForTransaction(...)`에 위임합니다[9].

`FlushMode.MANUAL`의 의미는 Hibernate `FlushMode` javadoc 원문 직접 확인[10]:

> "The Session is only flushed when `Session.flush()` is called explicitly. This mode is very efficient for read-only transactions."

즉 readOnly 트랜잭션에서는 Hibernate가 auto-flush/dirty-checking flush를 하지 않아(명시적 `flush()` 전까지) 우발적 write가 새지 않고 read에 효율적입니다. (기본값 `AUTO`는 "쿼리 전 stale 방지를 위해 때때로 flush"하며, `COMMIT`/`ALWAYS`도 javadoc[10]에 정의돼 있습니다.)

> 보강 필요(미확인): "readOnly 플래그가 Spring 5.1+에서 Hibernate `Session` 자체의 read-only 모드(엔티티 read-only 마킹)로도 전파된다"는 *별개* 주장은 vladmihalcea.com 차단으로 원문 직접 확인하지 못했습니다. 단, 본 결정의 근거인 **"readOnly → FlushMode.MANUAL → auto-flush 생략"** 동작은 위 [9][10] 1차 소스로 확인됐습니다.

### 2-3. JDBC 커넥션 레벨 효과: `Connection.setReadOnly(true)`

Java 21 `java.sql.Connection#setReadOnly(boolean)` Javadoc 원문(직접 fetch 확인[5]):

> "Puts this connection in read-only mode **as a hint to the driver to enable database optimizations.**"

또한 같은 Javadoc은 이 메서드를 트랜잭션 도중에 호출할 수 없다고 명시합니다("This method cannot be called during a transaction"). 즉 read-only 설정은 트랜잭션 시작 경계에서 적용되며, 효과는 드라이버 재량입니다.

- read replica 라우팅: Aurora MySQL을 replication URL로 연결한 경우, 커넥션이 read-only로 표시되면 드라이버가 쿼리를 ReadOnly Endpoint로 자동 redirect합니다. (WebFetch 차단 — [6] 검색 요약 기반)
- MySQL은 read-only 트랜잭션에서 write를 시도하면 에러를 던집니다. (WebFetch 차단 — [4] 검색 요약 기반)

---

## ③ Spring Data JPA repository 자체 트랜잭션

Spring Data JPA 공식 docs "Transactionality" 원문(직접 fetch 확인[1]):

> "By default, methods inherited from `CrudRepository` inherit the transactional configuration from `SimpleJpaRepository`. For read operations, the transaction configuration `readOnly` flag is set to `true`. All others are configured with a plain `@Transactional` so that default transaction configuration applies."

즉:

- `SimpleJpaRepository`는 **클래스 레벨에 `@Transactional(readOnly = true)`**를 갖습니다. 따라서 `findById`/`findAll`/`existsById`/`count` 같은 read 메서드는 이미 read-only 트랜잭션 경계에서 실행됩니다.
- `save`/`delete` 등 write 메서드는 메서드 레벨에서 plain `@Transactional`로 override되어 read-write 트랜잭션으로 실행됩니다.

이 클래스 레벨 `@Transactional(readOnly = true)` 설계는 커뮤니티에서 부수효과(예: write 메서드 override 누락 시 read-only 커넥션에서 JDBC 예외)가 보고될 만큼 실제로 동작한다는 점이 GitHub 이슈에서 거듭 확인됩니다(이슈 목록은 WebSearch로 확인[2]; 개별 이슈 본문은 WebFetch 차단으로 직접 인용 미확인).

override 방법도 docs에 명시(직접 fetch 확인[1]):

```java
public interface UserRepository extends CrudRepository<User, Long> {
  @Override
  @Transactional(timeout = 10)
  List<User> findAll(); // readOnly 플래그 없이 실행됨
}
```

**함의:** 우리 `BrandService.get`/`list`는 결국 `BrandJpaRepository`(Spring Data) 메서드 한 번 호출로 끝납니다. 그 메서드는 이미 자체 read 트랜잭션을 갖습니다. service 레벨에 다시 `@Transactional(readOnly = true)`를 얹어도, **단일 쿼리** 케이스에서는 추가 경계가 동작을 바꾸지 않습니다.

---

## ④ readOnly가 유익한 경우 vs redundant한 경우

**유익(서비스 레벨 read 트랜잭션이 값을 만드는 경우):**

1. **다중 쿼리 일관 스냅샷/단일 커넥션**: 한 service 메서드가 repository를 여러 번 호출할 때, 모두 하나의 read 트랜잭션·하나의 커넥션·하나의 영속성 컨텍스트로 묶입니다. 메서드별 자동 트랜잭션이 여러 개 생기는 것을 막습니다[1][4].
2. **managed entity read + 우발적 flush 차단**: 같은 트랜잭션에서 엔티티를 읽고 의도치 않게 setter가 호출돼도 FlushMode MANUAL이면 flush로 새지 않습니다(Spring `HibernateJpaDialect` 소스 + Hibernate `FlushMode` javadoc 확인[9][10]).
3. **read replica 라우팅**: 드라이버가 `Connection.setReadOnly(true)` hint를 라우팅에 사용하는 환경[5](Aurora 등, WebFetch 차단 — [6] 검색 요약 기반).
4. **lazy 접근을 위한 세션 경계**: 매핑/렌더링에서 lazy association을 건드려야 하면 read 트랜잭션이 세션을 열어둡니다(OSIV 꺼진 환경에서 특히 — ⑤ 참조).

**Redundant(우리 단건/목록 케이스):**

- 호출이 **단일** Spring Data repository 쿼리로 끝나고,
- 매핑이 eager/basic 필드만 읽어 영속성 컨텍스트 생존이 필요 없고,
- replica 라우팅을 쓰지 않으면,

→ repository가 이미 제공하는 read 경계 위에 service 레벨 read 트랜잭션을 다시 얹는 것은 동작상 잉여입니다. (이 redundancy 판단은 ①~③의 검증된 사실에서 도출한 본 문서의 결론이며, "단일 read엔 service tx가 redundant"라고 명시한 단일 출처 원문은 확보하지 못했습니다 — 추론 근거임을 명시.)

---

## ⑤ OSIV(open-in-view)와의 관계

Spring Boot 공식 docs(직접 fetch 확인[8]):

> "If you are running a web application, Spring Boot by default registers `OpenEntityManagerInViewInterceptor` to apply the 'Open EntityManager in View' pattern, to allow for lazy loading in web views. If you do not want this behavior, you should set `spring.jpa.open-in-view` to `false` in your `application.properties`."

즉 **OSIV는 Spring Boot 웹 앱에서 기본적으로 켜져 있고(default true)**, EntityManager를 요청 전체 동안 스레드에 bind해 view 단계의 lazy loading을 허용합니다.

### 본 프로젝트의 실제 설정 — 태스크 전제와 다름 (확인 결과)

> **정정:** 작업 의뢰 전제 중 "Spring Boot OSIV 기본 true(우리 앱 명시설정 없음)"는 이 저장소에서 사실이 아닙니다.

- `modules/jpa/src/main/resources/jpa.yml` 1~3행: `spring.jpa.open-in-view: false` (명시적으로 꺼져 있음).
- `apps/commerce-api/src/main/resources/application.yml`의 `spring.config.import`에 `jpa.yml`이 포함되어 commerce-api에 이 설정이 적용됩니다.

따라서 commerce-api는 **OSIV가 명시적으로 꺼진** 상태입니다. 함의:

- 컨트롤러/뷰/매핑 단계는 트랜잭션 밖이며, 거기서 lazy association을 건드리면 `LazyInitializationException`이 발생합니다[8](일반 OSIV-off 동작). 트랜잭션 경계 안에서 fetch join / `@EntityGraph` / DTO 변환으로 미리 로딩해야 합니다(WebSearch 요약 기반 — 본문 직접 인용은 [8] docs가 경고 메시지/예외 디테일을 다루지 않아 미확인).
- 단, **우리 `Brand`/`BrandInfo`는 lazy association이 전혀 없으므로** 이 위험에 해당하지 않습니다(아래 ⑥ 확인 결과).
- 또한 OSIV가 켜져 있더라도 그것이 가려주는 건 "웹 요청 처리 경로"이며, `@DataJpaTest`/repository 슬라이스 테스트나 비-웹 경로에는 OSIV 인터셉터가 적용되지 않습니다(일반 통념 — 본 문서 추론, 단일 출처 원문 미확인). 매핑이 lazy를 건드린다면 테스트에서 먼저 깨집니다.

---

## ⑥ 우리 프로젝트 결정과 이유

**결정:** `BrandService`에서 read 메서드(`get`, `list`)의 `@Transactional(readOnly = true)`를 제거하고, write 메서드(`register`, `update`, `delete`)만 `@Transactional`(read-write)을 유지합니다. (현재 코드가 이미 이 상태임을 확인: `apps/commerce-api/src/main/kotlin/com/loopers/application/brand/BrandService.kt` — `get`/`list`에 어노테이션 없음, `register`/`update`/`delete`에 `@Transactional`.)

**근거 검증 결과:**

1. **단일 쿼리 + repository 자체 트랜잭션** — `get`은 `findActiveById` 한 번, `list`는 `findByStatusNot` 한 번 호출로 끝납니다(`BrandRepositoryImpl` 확인). Spring Data repository read 메서드는 이미 `readOnly = true` 경계를 갖습니다[1][2]. → service 레벨 read 트랜잭션 잉여. **검증됨.**
2. **매핑이 eager/basic 필드만 접근** — `Brand` 엔티티(`domain/brand/Brand.kt`)는 `@Embedded BrandName` VO + `description`(basic) + `status`(enum)뿐이고 lazy `@OneToMany`/`@ManyToOne`이 없습니다. `BrandInfo.from`은 `id`, `name.value`, `description`만 읽습니다. → 영속성 컨텍스트/세션 생존 불필요. **검증됨.**
3. **OSIV** — 의뢰 전제(기본 true, 명시설정 없음)와 달리 이 저장소는 `open-in-view: false`로 명시 설정돼 있습니다(⑤). 다만 위 2번 때문에 lazy 접근 자체가 없어 OSIV on/off가 `Brand` 매핑 결과를 바꾸지 않습니다. → 결정에 영향 없음(전제는 정정). **검증됨(전제 정정).**

**write 유지 이유:** `register`/`update`/`delete`는 영속성 상태 변경 + dirty checking 기반 flush(예: `update`는 `brand.update(...)`, `delete`는 `brand.transitionTo(...)` 후 트랜잭션 커밋 시 flush)와, "존재 검증 + 중복 검증 + 변경"을 하나의 원자 경계로 묶어야 하므로 read-write `@Transactional`이 필요합니다.

**주의(향후):** `BrandService.delete`에 "후속(Product 머지 후): 이 브랜드의 Product soft delete cascade — BrandFacade에서 조합"이라는 주석이 있습니다. read 메서드가 향후 여러 도메인/여러 쿼리를 조합하거나 lazy graph를 매핑하게 되면, ①의 표 "다중 read / lazy 접근" 행에 따라 `@Transactional(readOnly = true)` 재도입을 재검토해야 합니다.

---

## ⑦ 출처 목록

| # | 출처 | 확인 방식 |
|---|---|---|
| [1] | Spring Data JPA Reference — Transactionality: https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html | WebFetch 직접 확인 (read 메서드 `readOnly=true`, write는 plain `@Transactional`, override 예시 원문 인용) |
| [2] | Spring Data JPA GitHub 이슈들 (SimpleJpaRepository 클래스 레벨 `@Transactional(readOnly=true)` 관련): #3188, #600, #2226, #2830, #1126 — https://github.com/spring-projects/spring-data-jpa/issues/3188 등 | WebSearch로 이슈 목록/요지 확인. 개별 이슈 본문은 WebFetch 차단으로 직접 인용 미확인 |
| [3] | Spring Framework Javadoc — `@Transactional` (`readOnly` 정의): https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html | WebFetch 직접 확인 (hint, silently ignore 원문 인용) |
| [4] | "@Transactional readOnly가 FlushMode.MANUAL/Session read-only/메모리 절감/MySQL write 차단으로 동작" | WebFetch **차단**(vladmihalcea.com 외 도메인 권한 거부). WebSearch 요약기 기반 — 원문 직접 확인 권장. 관련 원문 추정: Vlad Mihalcea, https://vladmihalcea.com/spring-read-only-transaction-hibernate-optimization/ |
| [5] | Java 21 Javadoc — `java.sql.Connection#setReadOnly`: https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html | WebFetch 직접 확인 ("as a hint to the driver to enable database optimizations" + 트랜잭션 중 호출 불가 원문 인용) |
| [6] | Aurora MySQL replication URL + read-only 커넥션 → ReadOnly Endpoint 라우팅 | WebSearch 요약 기반 (원문 직접 확인 미완). 관련: Vlad Mihalcea "Read-write and read-only transaction routing with Spring", https://vladmihalcea.com/read-write-read-only-transaction-routing-spring/ |
| [7] | Hibernate ORM 6.6 User Guide — "7. Flushing"(FlushMode AUTO/COMMIT/ALWAYS/MANUAL), "16.3.7 Querying for read-only entities": https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html | WebFetch 시도 — 단일 페이지 길이 초과로 **섹션 목차만 확인**, 각 모드/read-only 본문 정의는 추출 실패(미확인) |
| [8] | Spring Boot Reference — SQL Databases (Open EntityManager in View): https://docs.spring.io/spring-boot/reference/data/sql.html | WebFetch 직접 확인 (OSIV 기본 등록, `open-in-view=false`로 disable 원문 인용). "경고 로그" 부분은 이 docs에 없어 미확인 |
| [9] | Spring Framework `HibernateJpaDialect` 소스 — `beginTransaction`에서 `readOnly`면 `session.setHibernateFlushMode(FlushMode.MANUAL)`, 커넥션 read-only는 `DataSourceUtils.prepareConnectionForTransaction`에 위임, 종료 시 flush mode 복원: https://github.com/spring-projects/spring-framework/blob/main/spring-orm/src/main/java/org/springframework/orm/jpa/vendor/HibernateJpaDialect.java | WebFetch(raw GitHub) 직접 확인 (readOnly→FlushMode.MANUAL 코드 원문 인용) |
| [10] | Hibernate ORM 6.6 Javadoc — `org.hibernate.FlushMode`: MANUAL("only flushed when Session.flush() is called explicitly. This mode is very efficient for read-only transactions"), AUTO(기본, 쿼리 전 때때로 flush), COMMIT, ALWAYS: https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/FlushMode.html | WebFetch 직접 확인 (각 상수 정의 원문 인용) |

### 본 문서에서 직접 fetch로 확인한 사실 (검증됨)

- `readOnly`는 트랜잭션 subsystem에 대한 hint이며 미지원 시 조용히 무시될 수 있음 [3].
- Spring Data JPA repository의 read 메서드는 기본적으로 `readOnly = true` 경계를 가지며, write 메서드는 plain `@Transactional`로 override됨 [1].
- `Connection.setReadOnly(boolean)`은 드라이버에 대한 최적화 hint이고 트랜잭션 중에는 호출 불가 [5].
- Spring Boot 웹 앱은 OSIV를 기본 등록하며 `spring.jpa.open-in-view=false`로 끌 수 있음 [8].
- **Spring은 readOnly 트랜잭션에서 Hibernate Session flush mode를 `FlushMode.MANUAL`로 전환**하며(`HibernateJpaDialect` 소스 [9]), `MANUAL`은 명시적 `flush()` 전까지 flush하지 않음(Hibernate `FlushMode` javadoc [10]) — readOnly의 auto-flush 생략 효과는 1차 소스로 확인됨(이전 "검색 요약 기반"에서 승격).
- (저장소 코드) commerce-api는 `jpa.yml`을 import하고 거기서 `open-in-view: false`로 설정 — 태스크 전제(기본 true) 정정.
- (저장소 코드) `Brand`/`BrandInfo`에 lazy association 없음, `get`/`list`는 단일 repository 쿼리.

### 직접 fetch로 확인하지 못한 사실 (미확인 — 표기)

- ~~`readOnly=true`의 Hibernate FlushMode MANUAL 전환~~ → **[9][10]로 1차 소스 확인 완료(승격)**. 남은 미확인: Session 자체의 read-only **전파**(엔티티 read-only 마킹, Spring 5.1 전후) / MySQL write 차단 동작 — [4] vladmihalcea 차단으로 검색 요약 기반.
- Aurora 등 드라이버의 read replica 라우팅 구체 동작 — [6]. 검색 요약 기반.
- Hibernate FlushMode 각 값 및 read-only 엔티티 본문 정의 — [7]. 섹션 존재만 확인.
- "단일 read엔 service tx가 redundant"라는 명시적 단일 출처 원문 — 미확보. 본 문서의 ④/⑥ redundancy 결론은 [1][3][5]에서 도출한 **추론**.

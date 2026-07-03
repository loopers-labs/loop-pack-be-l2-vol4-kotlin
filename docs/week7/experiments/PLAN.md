# 선착순 쿠폰 발급 — 실험 기반 아키텍처 진화 계획 (loopers Round 7 Step 3)

> **사용법**: Phase 착수 전 이 파일을 읽는다. 실험 1건 완료 시마다 체크박스 갱신 + 같은 폴더에 `EXP-NN-<이름>.md` 기록 생성.

---

## 1. 목적·원칙 (전 Phase 공통)

- 모든 아키텍처 진화는 **실측 근거로만** 진행. 사이클: 가설 → 구현 → 정합성 검증 → 부하 실험 → 병목 진단 → 기록
- **정합성 불변식** (전 Phase 하드 제약, 부하 전 회귀 테스트 green 필수):
  1. 발급 성공 ≤ 한도(100)
  2. 동일 userId 중복 발급 0건
  3. 거절된 요청도 결과 조회 가능
  - 검증: 기존 `runConcurrently` 유틸(`support/ConcurrencyTesting.kt`) — 한도 100 + 동시 1,000 요청 → 정확히 100건·중복 0
- **NFR**: 당장의 처리량 + 최종적 일관성 우선. 발급 확정은 늦어도 OK, 단 **10초 이내**
- **최종적 일관성의 경계**: 사용자가 잠시 PENDING을 보는 것·조회가 잠시 낡은 것은 허용. 그러나 "발급 확정 응답한 건이 증발"(lost write)은 불허 — **확정의 내구성은 타협 불가**
- 단일 미니PC 몰빵 구성이므로 절대값이 아니라 **변형 간 상대 비교와 병목 위치**만 결론으로 채택
- **거절 경로도 측정 대상**: 100장은 수 초 내 소진 → 실부하의 대부분은 매진 거절 경로

## 2. 측정 체계 (최종 합의)

| 구분 | 내용 |
|---|---|
| 주 지표 = 응답 경계 (모든 Phase 동일, k6) | RPS, p50/p99 응답시간, 에러율, 상태코드 분포 |
| Phase별 response 계약 차이 (기록에 명시) | 동기(P1~P3) response=발급 확정 / 비동기(P4) response=접수 확인. 지표 결함이 아니라 **제품 계약 변경** |
| 확정 게이트 (pass/fail, 지표 아님) | `time_to_decision ≤ 10초`. k6 커스텀 메트릭으로 클라이언트 관점 측정. 게이트가 깨지면 그 런의 접수 수치는 "접수만 빠르고 확정 파산"으로 **무효 처리** — 비동기가 접수 RPS만으로 승리 선언 금지 |
| Phase 4 부하에 polling 포함 | 각 VU가 POST 접수 → 1초 간격 GET polling → 확정까지(최대 10초). 이유: ① 1만 명 동시 polling이면 GET이 POST보다 큰 부하일 수 있음 — read path 실측 포함 ② time_to_decision이 클라이언트 관점으로 산출 |
| 서버측 교차 검증 | `coupon_issue_result.requested_at` / `decided_at` |
| 시스템 내부 지표 (진단용, 결론 아님) | Tomcat busy threads, HikariCP active/pending/acquire p99, JVM heap/GC pause, 컨테이너별 CPU·메모리(cAdvisor), 호스트 CPU(node-exporter), innodb_row_lock_waits/time·threads_running(mysqld-exporter), consumer lag(P4) |

**런 프로토콜**
- 시나리오당 3회 반복·중앙값 채택
- 런 간 데이터 리셋
- 기록에 커밋 해시 + 설정값(풀 크기 등) 포함
- Prometheus scrape 5초 (스파이크 10초짜리가 15초 간격엔 안 보임)
- 런 중 맥 `caffeinate`

## 3. 인프라 토폴로지

| 위치 | 역할 |
|---|---|
| 홈서버 (피스큐브 미니PC, Ubuntu Server 26.04, 전부 Docker) | **SUT만**: commerce-api, MySQL, Redis(P3~), Kafka+commerce-streamer(P4~) + exporter 4종(node-exporter, cAdvisor, mysqld-exporter, redis-exporter) |
| 맥 | k6 부하 + Prometheus(LAN 원격 scrape) + Grafana (docker compose). 관측 스택을 SUT 밖으로 빼서 측정 오염 제거 |

- **compose profile 단계 기동**: `core`(commerce-api+MySQL) / `p3`(Redis) / `p4`(Kafka·streamer) — 안 쓰는 스택이 앞 Phase 결과를 오염시키는 것 차단
- **이미지 빌드**: 홈서버에서 git pull 후 빌드로 통일 (맥 arm64 vs 서버 x86_64 크로스 빌드 함정 회피)
- **네트워크 전제조건**: 공유기 뒤 이사 + 내부 IP 고정 (exporter 포트를 LAN에만 노출하기 위해 필수). **미완료 — 아직 공인 IP 직결**
  - 서버 켜면 검증할 것: ① `hostname -I` 현재 IP ② `sudo ufw status verbose` ③ `docker ps` publish 포트 ④ 외부 포트스캔
  - 참고 사실: SSH 키는 22번 포트만 보호하며, Docker publish 포트는 iptables 직접 규칙으로 ufw를 우회함 — 지난번 k6가 외부에서 통했던 유력 원인
  - **2026-07-03 검증 결과** (원격 SSH, `won@222.107.95.24` — enp1s0, DHCP 동적이라 재부팅마다 재확인 필요):
    - ① 현재 IP `222.107.95.24` (`172.17.0.1`은 Docker 기본 브리지 게이트웨이, 컨테이너 미기동으로 DOWN — 무관)
    - ② `ufw`: `systemctl is-active/is-enabled` 기준 **active+enabled** 확인. 세부 rule set(`ufw status verbose`)은 일반 계정 sudo 비번 필요해 원격 비대화형으로는 미확인
    - ③ `docker ps -a`: 실행 중 컨테이너 0개(과거 컨테이너 1개 2주 전 Exited) → publish된 포트 없음. `ss -tlnp` 기준 리스닝 포트도 22(SSH)·로컬 DNS뿐
    - ④ 외부 포트스캔: 미실행
    - **여전히 공인 IP 직결 + DHCP** → 이 상태로 컨테이너를 publish하면 ufw 우회 위험이 그대로 살아있음(위 "참고 사실" 그대로)
  - **결정 (2026-07-03)**: 공유기 이사를 실험 착수의 블로킹 조건으로 두지 않는다. 대신 **컨테이너 포트를 publish 하지 않거나 `127.0.0.1`에만 bind**하고, 맥에서 `ssh -L <port>:localhost:<port> won@<현재IP>` 터널로 k6/Prometheus가 필요한 포트에만 접근한다 — 공인 IP에 포트가 하나도 열리지 않아 위 위험이 사라짐. 공유기 뒤 이사 + 내부 IP 고정은 별도 후속 작업으로 계속 추적(YAGNI 아님 — 장기적으로 필요하지만 지금 실험의 블로커는 아님)
- 부하 실험은 **집(맥·서버 같은 LAN)에서만** 수행. WAN 너머 측정치는 기록에서 분리
- 관측 스택 셋업은 홈서버 문서 `~/Coding/homeserver/observability-practice.html` 의 **Stage 0~3 절차 재사용** (percentiles-histogram, RED+HikariCP 대시보드, PromQL 완비)

## 4. Phase 0 — 기반 구축

### 0-a 홈서버 (서버 켠 뒤, 사용자 물리 작업 포함)

- [ ] 네트워크 실태 검증 (3장의 4항목: `hostname -I` / `ufw status verbose` / `docker ps` publish 포트 / 외부 포트스캔) — 2026-07-03 원격 SSH로 ①③ 완료, ② active/enabled만 확인(세부 rule set은 sudo 비번 필요해 미확인), ④ 미실행. 서버 직접 접속 시 `sudo ufw status verbose` + 외부 포트스캔 이어서
- [ ] 공유기 뒤 이사 + 내부 IP 고정 — 2026-07-03 기준 여전히 미완료(공인 IP `222.107.95.24`, DHCP 동적). 실험 착수의 블로커는 아님(§3 "결정" 참고 — SSH 터널로 우회), 별도 후속 작업으로 추적
- [ ] 서버 스펙 기록 (`nproc`, `free -h`, 디스크) — 실험 조건으로 문서화

### 0-b 코드·스크립트 (서버 불필요, 즉시 가능)

- [x] 브랜치 `feature/week07-fcfs-coupon-issue` (워크트리 `loop-pack-week07-fcfs-coupon`, PR [#6](https://github.com/shoeone96/loop-pack-be-l2-vol4-kotlin/pull/6) → feature/week07-event-driven)
- [x] Coupon에 `total_quantity`/`issued_quantity` 추가, `UserCouponGrantedType.FIRST_COME` 추가
- [x] `POST /api/v1/coupons/issue` (동기, Phase 1용), 매진 시 `ConflictException(SOLD_OUT)` — 비관적 락(`findByIdForUpdate`), inventory 선례
- [x] `runConcurrently` 정합성 테스트 — 동시 1,000 → **300으로 조정** (테스트 Hikari 풀 10이라 그 이상은 커넥션 대기열만 검증 + 10초 타임아웃 flaky 위험. 1만 스파이크는 Phase 1 k6가 담당). 결과: 한도 100 → 정확히 100건·초과 전부 SOLD_OUT·동일 userId 1건
- [ ] commerce-api Dockerfile + compose(profile 구조) + 시드 스크립트: 유저 1만+, 쿠폰 2종(한도 100 스파이크용 / 한도 십만 지속 경합용)
- [ ] k6 시나리오 2종: S1 스파이크(10초 내 1만 요청 — 오픈런), S2 계단(arrival rate 100→200→400→800/s 각 3~5분 — 한계 탐색)
- [ ] actuator + micrometer-registry-prometheus 의존성

## 5. Phase 1 — DB-only 3변형 비교

| 변형 | 방식 | 가설 |
|---|---|---|
| A 비관적 락 | `SELECT ... FOR UPDATE` 후 차감 | 락 대기 직렬화, 커넥션 점유 최장 |
| B 조건부 원자 UPDATE | `UPDATE ... SET issued_quantity=issued_quantity+1 WHERE issued_quantity < total_quantity` (affected rows 판정) | 락 구간 최소 — DB-only 중 최선 예상 |
| C 낙관적 락 + 제한 재시도 | `@Version` | 극단 경합에서 재시도 폭풍 — 반례용 |

- 공통 최종 방어: `uk_user_coupon` unique (userId 중복)

- [ ] 변형 A 구현 + 정합성 green + S1/S2 측정
- [ ] 변형 B 구현 + 정합성 green + S1/S2 측정
- [ ] 변형 C 구현 + 정합성 green + S1/S2 측정
- [ ] 변형별 EXP 기록 작성

**종료 기준**: 변형별 한계 RPS·p99·락 지표 확보, "DB-only 구조적 상한" 숫자 확정

## 6. Phase 2 — 병목 진단 + 가상 스레드 + 풀 튜닝

**진단 사다리** (지목 아닌 배제 순서, 각 단계 변수 하나만 바꿔 재실험으로 확정):

| 순서 | 확인 항목 |
|---|---|
| ① | Tomcat busy=max? |
| ② | Hikari pending>0 지속? |
| ③ | row lock waits 급증? |
| ④ | MySQL 컨테이너 CPU 포화? |
| ⑤ | 앱 CPU/GC? |

**실험 축1 — 가상 스레드**
- Spring Boot 3.4.4 `spring.threads.virtual.enabled=true` 한 줄. platform(Tomcat 200) vs virtual 동일 부하 비교
- 사전 가설: 병목이 락/커넥션 대기라서 대기 지점만 Tomcat→Hikari pending으로 이동, 총 처리량 거의 불변 — 확인되면 "가상 스레드는 blocking I/O 대기 스레드가 상한일 때만 유효" 실측 근거
- `-Djdk.tracePinnedThreads=full`로 pinning 관찰 (Java 21은 `synchronized`에서 캐리어 스레드 pinning)

**실험 축2 — 풀 튜닝**
- HikariCP 10→20→30 × Tomcat 200→400 (가상 스레드 축과 교차)
- 가드레일: MySQL 컨테이너 CPU 지속 ~80% 초과 시 증설 중단
- 몰빵 교란 변수: 판정은 DB CPU 단독이 아니라 **앱 CPU + lock waits + Hikari pending 세트**로 — 풀을 늘려도 RPS 불변 + lock wait만 증가하면 락 직렬화가 진범 = Phase 3 진입 근거

- [ ] 진단 사다리 수행 + 병목 확정
- [ ] 축1: platform vs virtual 비교 측정 + pinning 관찰
- [ ] 축2: 풀 교차 매트릭스 측정
- [ ] EXP 기록 작성

**종료 기준**: 튜닝 도달 가능 상한 + 구조적 병목의 이름을 문장으로 확정

## 7. Phase 3 — Redis

| 변형 | 방식 | 판단 |
|---|---|---|
| A (중심) | Lua 스크립트로 수량+userId 중복을 원자 선검증 → 통과분만 DB 동기 발급. DB가 확정의 진실 원천 유지 | DB 도달 트래픽이 "당첨 100+α"로 격감 |
| B (드롭 또는 참고 1회 측정) | Redis 즉시 확정 + DB 비동기 기록 | 10초 여유 NFR 하에서 Redis 유실 리스크를 질 이유 없음 (유일 장점 "확정 응답 빠름"이 무의미) |

- 정합성 방어선 유지: DB 조건부 UPDATE + unique 최종 방어 (Redis 재시작·drift 시 오버발급 차단)
- 병목 재진단: 앱 CPU 또는 Tomcat으로 이동 예상

- [ ] 변형 A 구현 + 정합성 green + 측정
- [ ] 부하 중 redis kill 후 정합성 유지 검증
- [ ] (선택) 변형 B 참고 1회 측정
- [ ] 병목 재진단 + EXP 기록 작성

**종료 기준**: Phase 2 상한 대비 개선 폭 수치 + 새 병목 명명

## 8. Phase 4 — Kafka 지연 처리 (과제 MUST 구간)

**구조**: `POST /api/v1/coupons/issue` → `coupon-issue-requests`(key=couponId, 파티션 1) 직발행 + requestId 즉시 응답 → 단일 컨슈머 순차 처리(수량 확인→발급/거절→결과 저장) → `coupon_issue_result`(PENDING→ISSUED/REJECTED, PK=requestId, requested_at/decided_at 컬럼) → `GET /api/v1/coupons/issue/{requestId}` polling

- **직발행 결정 근거**: 쿠폰 요청은 묶을 본 트랜잭션이 없어 Outbox 불필요 (Outbox는 주문/집계 파이프라인 MUST에서 수행). 발행 실패는 사용자에게 즉시 에러 응답
- **멱등**: `coupon_issue_result` PK=requestId, insert-first — Kafka 재전송이 와도 중복 차감 불가
- **과제 MUST 매핑**: `acks=all`·`enable.idempotence=true`(commerce-api application.yml의 `spring.kafka.producer` — 기존 modules:kafka KafkaConfig가 `buildProducerProperties()`로 읽음), manual ack, 파티션 키 순서 보장, 멱등 처리
- **선행 작업**: commerce-api `build.gradle.kts`에 `:modules:kafka` 의존성 추가 (현재 미연결)
- **backlog 공식**: 허용 backlog(최대 consumer lag) = consumer 처리량 X(건/s) × 10초 — lag 알람 임계·rate limit 수치가 계산으로 도출됨

- [ ] 선행: `:modules:kafka` 의존성 연결 + producer 설정
- [ ] 구조 구현 (직발행 → 컨슈머 → 결과 테이블 → polling GET)
- [ ] 정합성 green
- [ ] 실험 A (성능): 접수 RPS + polling 포함 부하 + time_to_decision 게이트 — consumer가 병목이면 배치 소비로 재실험
- [ ] 실험 B (정합성): 부하 중 consumer 강제 재시작 → 재전송 발생 → 중복 발급 0건 검증
- [ ] backlog 공식으로 lag 임계 산출 + EXP 기록 작성

**종료 기준**: 과제 합격 기준 3종 + 접수/확정 수치 확보

## 9. Phase 5 — Resilience + Rate Limit

**장애 주입 매트릭스** (docker stop/pause):

| 장애 | 관찰 대상 | 결정 사항 |
|---|---|---|
| Redis down | API 에러율, DB 방어선 작동 | fail-fast vs DB 폴백 검토 (폴백은 동시성 모델이 달라져 위험 — 검토 후 결정) |
| Kafka down | 발행 실패 처리 | 즉시 503 vs 재시도·타임아웃 정책 |
| consumer down | lag 누적, 복구 후 따라잡기 시간 | lag 알람 임계값 (backlog 공식 활용) |
| DB down | 전 경로 | circuit breaker 필요 여부 |

- **Rate limit**: Phase 1~4 실측 용량 × 안전계수 ~0.7. 단일 인스턴스이므로 인메모리 Bucket4j부터 (YAGNI — 다중 인스턴스 시 Redis 기반 승격). 초과 429, k6로 발동 검증
- **알람**: 홈서버 관측 문서 Stage 4 규칙 재사용 (Hikari pending, 에러율 5%, p99 SLO)
- Nice-to-have: DLQ, consumer group 분리

- [ ] 장애 주입 4종 수행 + 각 결정 사항 확정
- [ ] Rate limit 구현 + k6 발동 검증
- [ ] 알람 규칙 적용
- [ ] EXP 기록 작성

## 10. 기록 체계

- 실험 1건당 `docs/week7/experiments/EXP-NN-<이름>.md` — **고정 5섹션**:
  1. 조건 (커밋·설정·스펙)
  2. 가설
  3. 수치
  4. 병목 판정
  5. 결론·다음 결정
- 이 기록이 과제 Technical Writing(④ Benchmark Report), 블로그("100장에 1만 명 — DB→Redis→Kafka 실측 진화기"), 홈서버 문서 09번 카드의 원고가 됨

## 11. 진행 순서

```
0-b (지금 가능, 서버 불필요) → 0-a (서버 켠 뒤) → P1 → P2 → P3 → P4 → P5  (직렬)
```

각 Phase는 **종료 기준 충족 후에만** 다음 진행.

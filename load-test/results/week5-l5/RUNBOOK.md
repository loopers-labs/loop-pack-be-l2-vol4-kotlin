# Week5 L5 — 상품 조회 성능 측정 RUNBOOK

> 이 문서는 **리포트 제출용 측정 저널**입니다. 모든 준비·결정·명령·결과를 시간순으로 누적합니다.
> 작성 시작: 2026-06-19 12:08 KST

---

## 1. 목적 & 범위

상품 목록/상세 조회 API의 **인덱스 적용 전후 성능 차이**를 실부하 환경에서 정량 측정한다.
이번 라운드는 **인덱스 전용**(캐시 보류). 캐시(Redis read path)는 본 측정 결과를 본 뒤 별도 라운드로 진행.

### 실험 매트릭스 (총 12 런)

| 축 | 값 |
|---|---|
| 인덱스 상태 | `baseline`(인덱스 없음) / `indexing`(복합 인덱스 적용) |
| 목표 TPS (offered load) | 100 / 1000 / 3000 |
| JVM 웜업 | `cold`(부팅 직후 측정) / `warm`(웜업 후 측정) |

2(인덱스) × 3(TPS) × 2(웜업) = **12 런**

### 부하 시간 규칙
- **웜업 런(warm 케이스 준비)**: 1분간 카운트(TPS) 올리며 램프업 → JIT 컴파일 유도
- **본 측정 런**: 모두 **3분** 일정 부하(constant-arrival-rate)로 추이 관찰

---

## 2. 환경 (Environment Baseline)

### 부하 발생기 (Load Generator) — 사용자 Mac
| 항목 | 값 |
|---|---|
| 아키텍처 | arm64 (Apple Silicon) |
| k6 | v1.7.1 (go1.26.1, darwin/arm64) |
| Docker | 28.0.4 |
| 역할 | k6 부하 발생 + Prometheus + Grafana (원격 스크랩) |

### 측정 대상 서버 (System Under Test) — N100 홈서버
| 항목 | 값 |
|---|---|
| 접속 | `ssh won@175.208.203.10` (공인 IP 직결) |
| CPU | Intel N100 4C/4T |
| RAM | 14Gi (+swap) |
| Disk | 466G NVMe (사용 7.5G) |
| OS | Ubuntu Server 26.04 LTS (헤드리스) |
| Docker | 29.5.3 |
| 초기 상태 | 컨테이너 0, Java 미설치, 레포 없음 (bare) |
| 역할 | commerce-api + mysql + redis + cAdvisor + node_exporter (전부 Docker) |

> N100 4코어를 app·mysql·redis가 공유 → **자원 경쟁(USE)** 관찰이 핵심.

---

## 3. 토폴로지

```
  ┌─────────────── 사용자 Mac (arm64) ───────────────┐         ┌──────────── N100 (amd64, 4C/14Gi) ────────────┐
  │  k6  ──HTTP 부하──────────────────────────────────┼────────▶│  commerce-api (eclipse-temurin:21-jre)        │
  │                                                   │  :8080  │    :8080 API  /  :8081 actuator/prometheus    │
  │  Prometheus ──scrape :8081,:8080cadvisor,:9100────┼────────▶│  mysql:8.0  redis-master:7  redis-readonly:7  │
  │  Grafana (Prometheus 대시보드)                     │         │  cAdvisor(:8080→컨테이너별 CPU/메모리)          │
  └───────────────────────────────────────────────────┘         │  node_exporter(:9100→호스트 CPU/메모리/PSI)    │
                                                                 └───────────────────────────────────────────────┘
```

- k6 → `http://175.208.203.10:8080/api/v1/products...`
- Prometheus(Mac) → N100의 3개 타깃 스크랩: 앱 actuator(8081), cAdvisor(컨테이너 지표), node_exporter(호스트 지표)
- commerce-api는 kafka 미사용 → perf 스택에서 kafka 제외 (자원 절약)

---

## 4. 배포 레시피 & 결정 근거

### 결정 1: jar 빌드(Mac) → temurin 컨테이너(N100) 실행
- Mac=arm64, N100=amd64로 아키텍처가 다름. 그러나 **bootJar는 아키텍처 독립**(바이트코드).
- N100에서 `eclipse-temurin:21-jre`(amd64 네이티브) 베이스에 jar를 bind-mount해 `java -jar`로 실행.
- → 로컬 크로스 빌드(buildx) 불필요, 빌드 1회로 끝. **근거: 단순성 + JRE만 amd64면 충분.**

### 결정 2: `perf` 프로파일 (app-owned application.yml에 추가)
- `modules/jpa/jpa.yml`는 베이스 템플릿(수정 금지). datasource override는 **app 소유** `commerce-api/.../application.yml`의 `perf` 프로파일 블록으로.
- perf 프로파일: `ddl-auto=none`, `show-sql=false`, datasource/redis 호스트는 컨테이너 서비스명(env 기본값).
- **근거: show-sql=true(local/dev)는 측정 오염, ddl-auto=create는 재시작 시 시드 파괴.** 측정 중 JVM cold/warm 재시작이 필요하므로 `none` 필수.

### 결정 3: 스키마 생성 1회 → 시드 → 측정
- 스키마는 첫 부팅 1회만 `SPRING_JPA_HIBERNATE_DDLAUTO=create`로 생성 후 중단.
- `perf-seed-l5.sql`(product_like ~600만) 적재.
- 이후 측정 부팅은 `perf` 프로파일(ddl-auto=none) → 재시작해도 데이터 보존.

### 결정 4: 인덱스 토글 (baseline ↔ indexing)
측정 사이 SQL로 인덱스를 붙였다/뗐다 한다. (출처: `load-test/measure-pagination.sh`)
```sql
-- indexing 조건
CREATE INDEX idx_p_lc_id       ON product(like_count, id);
CREATE INDEX idx_p_brand_lc_id ON product(brand_id, like_count, id);
-- baseline 조건 (되돌리기)
DROP INDEX idx_p_lc_id       ON product;
DROP INDEX idx_p_brand_lc_id ON product;
```
- baseline: PK(id)만 → `ORDER BY like_count DESC, id DESC`에 filesort 발생.
- indexing: 복합 인덱스로 정렬 제거(인덱스 순서 그대로 읽기).
- `idx_pl_pid`(product_like)는 방식 A(실시간 COUNT)용이라 이번(방식 B 컬럼) 측정엔 불필요.

---

## 5. 측정 지표 (USE 메서드 + JVM)

### 컨테이너별 (cgroup v2 직접 — `sample-cgroup.sh`, 런 전/후 델타)
> 당초 cAdvisor 계획이었으나 Docker 29 containerd 스냅샷터 비호환으로 폐기(§7 12:20~12:27 참조). cgroup v2 원천을 직접 읽음.
- **Utilization**: `cpu.stat`의 `usage_usec` 델타 ÷ 측정시간 = 평균 코어, `memory.current`
- **Saturation**: `cpu.stat`의 `throttled_usec`/`nr_throttled` (단, CPU 제한 미설정이라 0 → 포화는 호스트 PSI로 본다)
- app / mysql / redis **3개 컨테이너 모두** 측정 (4코어 경쟁 가시화)

### 호스트 (node_exporter)
- CPU: `node_cpu_seconds_total`(mode별 idle/user/system/iowait)
- 메모리: `node_memory_MemAvailable_bytes`
- **PSI(Pressure Stall Information)**: `node_pressure_cpu_waiting_seconds_total`, `node_pressure_memory_*` — 자원 포화의 직접 신호

### JVM (앱 actuator/prometheus, 8081)
- CPU: `process_cpu_usage`, `system_cpu_usage`
- **JIT 웜업**: `jvm_compilation_time_ms_total` (cold/warm 비교 핵심)
- GC: `jvm_gc_pause_seconds_*`
- 커넥션풀: `hikaricp_connections_*`(active/pending/usage)
- HTTP: `http_server_requests_seconds_*`(p50/p95/p99 히스토그램 — monitoring.yml에서 활성)

### k6 (부하측, 결과 산출)
- `http_req_duration`(p50/p90/p95/p99), `http_req_failed`, 실제 달성 throughput(`http_reqs` rate) vs offered TPS
- **포화 무릎(knee)**: offered↑인데 achieved 정체 = 천장 도달

---

## 6. 실행 순서

1. [진행중] jar 빌드 (Mac, background)
2. perf 프로파일 추가 → 재빌드 반영
3. N100: perf 스택 docker-compose 작성·전송
4. N100: mysql/redis 기동 → 앱 1회 부팅(ddl-auto=create)으로 스키마 생성 → 중단
5. N100: 시드 적재(perf-seed-l5.sql) + 불변식 검증(like_count == COUNT)
6. N100: cAdvisor + node_exporter 기동
7. Mac: Prometheus 타깃을 N100으로 변경 + Grafana 기동, 스크랩 확인
8. 스모크: 앱 부팅 + `/api/v1/products` 200 + actuator 지표 노출 확인
9. **측정 매트릭스 실행** (baseline 6런 → indexing 6런; 각 cold/warm × 100/1000/3000)
10. 런별 결과 수집(k6 요약 + Prometheus 스냅샷) → `runs/`에 저장

---

## 7. 실행 로그 (시간순 누적)

### 2026-06-19 12:08 — 사전 조사 완료
- N100 SSH 정상, bare 상태 확인. Mac k6 v1.7.1 확인.
- `/actuator/prometheus` 작동 확인 (supports:monitoring → micrometer-registry-prometheus, 8081).
- Product GET·actuator `permitAll`(인증 불필요), commerce-api kafka 미사용 확인.
- 인덱스 토글 DDL 확정(measure-pagination.sh 출처).
- jar 빌드 시작(background, task blszk90l4).

### 12:09 — perf 프로파일 추가 + 재빌드
- `commerce-api/.../application.yml`에 `perf` 프로파일 블록 추가(ddl-auto=none, show-sql=false, env-driven datasource/redis).
- 재빌드 → `commerce-api-964c029.jar`. unzip으로 perf 프로파일 포함 확인.

### 12:11~12:12 — N100 전송
- `~/perf/` 생성. perf-stack-compose.yml + perf-seed-l5.sql + app.jar(80M) scp 완료.

### 12:13 — 인프라 기동 (mysql/redis)
- `docker compose up -d mysql redis-master redis-readonly` → 이미지 pull 후 전부 healthy.
- mysql: innodb-buffer-pool-size=2G(양 조건 동일), utf8mb4.

### 12:13~12:14 — 스키마 생성
- 앱 1회 부팅(`SPRING_JPA_HIBERNATE_DDLAUTO=create`, temurin:21-jre pull) → 11개 테이블 생성 확인.
- product 컬럼: id(PK)·brand_id·like_count·status·price·name·*_at 정상. schema-init 컨테이너 제거.

### 12:15 — 시드 적재 시작 (background b1nzp8hh3)
- `docker exec -i perf-mysql mysql ... < perf-seed-l5.sql` (~600만 product_like).

### 12:14~12:15 — 모니터링 기동
- N100: cadvisor(:8082), node-exporter(:9100) 기동.
- Mac: Prometheus(:9090) + Grafana(:3000) 기동, 타깃을 N100으로.
- **원격 스크랩 검증**: cadvisor `up`, node-exporter `up`. commerce-api `down`(앱 미기동, 정상).
- Docker publish가 ufw 우회 → 8080/8081/8082/9100 Mac에서 도달 확인.

### 12:17 — 시드 완료 & 검증
- product 100,750 / product_like 6,814,195 / brand 500.
- 티어 분포: 메가5(40k) 대형20(30k) 중형75(18.75k) 소형400(12k).
- **불변식 검증 통과**: SUM(like_count)=COUNT(product_like)=6,814,195. max like_count=58,991.
- 초기 인덱스 = PRIMARY(id)뿐 → **baseline 상태로 시작**.

### 12:18~12:19 — 앱 기동 & 스모크
- commerce-api perf 프로파일 기동(ddl-auto=none), health 200(~18s 부팅).
- Mac→N100 공인IP 스모크: 상품목록 200(좋아요순 정렬 정확, top=58991), **baseline 첫 요청 0.48s**(10만 행 filesort), 브랜드필터 200/0.10s.
- actuator: jvm_compilation_time_ms_total/process_cpu_usage/hikaricp 노출. Prometheus 3타깃 `up`.

### 12:20~12:27 — ⚠️ cadvisor 폐기 → cgroup 직접 샘플링으로 전환 (중요 결정)
- **문제**: cadvisor가 컨테이너별 지표를 전혀 못 냄. 진단 결과 2단계 원인:
  1. cgroupns=private → cadvisor가 호스트 cgroup 트리 못 봄 → `cgroup: host` 로 해결.
  2. 그 후에도 "Failed to create existing container … image/overlayfs/layerdb/mounts/…/mount-id: no such file": **Docker 29 = containerd 스냅샷터**(`io.containerd.snapshotter.v1`)라 classic layerdb 부재 → cadvisor 컨테이너 디스커버리 실패(알려진 비호환).
- **해결**: cadvisor 제거. 컨테이너별 CPU/메모리는 **cgroup v2 `cpu.stat`(usage_usec) + `memory.current` 직접 샘플링**(`sample-cgroup.sh`). cadvisor가 읽는 바로 그 원천이라 동등하며, 런별 CPU-초 델타라 더 정확.
- **CPU 제한 미설정** → cpu.stat throttling=0(의도적: 4코어 자유 경쟁). 포화 신호는 **호스트 PSI**(node_pressure_cpu_waiting)로.
- 샘플러 검증: app 66.8 CPU-s/1.42GB, mysql 158.6 CPU-s/3.28GB, redis 각 ~11.5 CPU-s/idle.
- Prometheus 정리: cadvisor 타깃 제거 → commerce-api + node-exporter `up`.

### 최종 측정 데이터 소스
| 소스 | 수집 | 지표 |
|---|---|---|
| node_exporter → Prometheus | 5s scrape | 호스트 CPU(mode별), 메모리, **PSI**(포화) |
| actuator/prometheus → Prometheus | 5s scrape | process_cpu, **jvm_compilation_time**(JIT), gc_pause, hikaricp, http p99 |
| cgroup v2 직접(sample-cgroup.sh) | 런 전/후 | **컨테이너별 CPU-초·메모리**(app/mysql/redis) |
| k6 (Mac) | 런 종료 summary | p50/p90/p95/p99, 실패율, achieved RPS |

### 12:32~12:36 — 오케스트레이터 작성 & 스모크 검증
- `run_matrix.py`(Mac): 런마다 앱 재시작(cold/warm) → 인덱스 토글 → k6 → cgroup 델타 + Prometheus(host/JVM) 수집 → `runs/<id>.{json,md}` + `matrix-results.csv`.
- k6 파서 1차 None → k6 v1.x summary는 통계가 metric 최상위(`values` 래퍼 없음), 실패율 키 `value`로 수정.
- **스모크(baseline 100 cold, 30s) 결과 — 이미 강력**:
  - achieved 64rps(목표 100 미달), 실패 17%, **p95 11.9s / p99 12.1s**
  - **mysql 3.0코어**(10만 행 filesort), app 0.93코어, 호스트 CPU 92%(max 100%)
  - **PSI cpu waiting Δ 36s/42s**(심각 포화), Hikari pending 160(풀 고갈), JIT Δ 27.8s
  - → baseline은 100 TPS도 못 버팀. 인덱스 효과가 클 것.

### 12:36 — 전체 매트릭스 1차 착수 (background brm6d1t65)
- 12런: baseline{100,1000,3000}×{cold,warm} → indexing 동일. 각 측정 3분(+warm은 웜업 1분).

### 13:38 — ⚠️ 방법론 교정: 요청 타임아웃 도입 (1차 중단)
- **문제 발견**: baseline-3000-cold가 **36분 소요**, p95=**1,084,672ms(18분)**, achieved 9.5rps, host CPU 메트릭 음수(-3.5).
- **원인**: k6에 요청 타임아웃 부재 → 서버 포화 시 요청이 Tomcat accept 큐에 수 분~수십 분 적체. constant-arrival-rate가 VU를 다 소진하고, gracefulStop도 없어 in-flight 요청을 무한정 대기 → "포화"가 아니라 **무한 큐잉 아티팩트**. 36분 윈도우라 node_exporter rate 계산도 왜곡.
- **교정**:
  1. k6 `http.get(url, {timeout:'10s'})` — **프론트 표준 타임아웃**(axios 관례 `timeout:10000` + Nielsen "10초 주의 한계" UX 임계값) 초과 = 사용자에겐 실패. 포화를 "10s SLA 위반율"이라는 의미 있는 신호로 측정.
  2. 시나리오 `gracefulStop:'10s'` — 요청이 10s 안에 종결되므로 런도 duration+~10s 로 bounded.
  3. host CPU 쿼리 `clamp(…,0,1)` + rate 윈도우 [1m] — 메트릭 왜곡 방지.
- 1차 결과(무제한 큐잉) 폐기. 전체 재실행. **교정 사유 자체가 리포트의 측정 설계 근거가 됨.**

### 13:44~14:36 — 전체 매트릭스 2차 (완료, 12런)
- 첫 런 bounded 검증(180s). 12런 전부 ~3분에 마감(36분 행 없음).
- 핵심: 인덱스 100 TPS p95 9,997→13.9ms(719×)·실패 82→0% · 1000 TPS 달성 132→978rps · 천장 ~100→~950rps(약 10×).
- 웜업: indexing 1000 cold p95 8,345ms(JIT Δ 384s)→warm 781ms. baseline은 cold≈warm(DB 바운드).
- 병목 이동: baseline mysql 2.49코어(filesort) → indexing app 2.01코어(JVM). PSI·Hikari도 동반.

### 14:38~14:40 — EXPLAIN 전후 + 리포트
- `explain-capture.sh`: baseline Table scan(100,750)+Sort 52.3ms → indexing Index scan(reverse) 0.106ms(Sort 소멸). 브랜드 0.45ms, keyset 0.51ms. → `explain-before-after.txt`.
- `build_report.py` → `ANALYSIS.md`(12런 집계). `build_html.py` → `docs/week5/09-load-test-results.html`(toss-design, SVG 3종). 크롬 오픈.

### 16:00~16:13 — 첫 페이지 캐시 구현 & 실험 착수 (background bajfy7tdf)
- **구현**: 글로벌 첫 페이지(브랜드X·커서X)만 캐시. 컨트롤러가 `brandId==null && cursor==null`일 때만 `ProductListQuery.firstPage()`(`@Cacheable sync=true`, 별도 빈→self-invocation 회피) 호출. 그 외는 인덱스 직행.
  - `CacheConfig`: RedisCacheManager, TTL 5s, 앱 ObjectMapper 재사용 type-bound 직렬화기(`ProductListResponse`).
  - 토글: `SPRING_CACHE_TYPE=redis(on)/none(off)` env → 앱 컨테이너 재생성.
- **스모크 검증**: 1st 370ms(미스)→2nd 12ms(히트), Redis 키 `productListFirstPage::LIKES_DESC:20` TTL 5s. 캐시 응답=비캐시 응답 동일(top 58991), 브랜드필터는 캐시 우회 확인.
- **실험**: indexing·warm·**글로벌 첫페이지 100%**(BRAND_RATIO=0), cache off vs on × {1000,3000} TPS. Redis 적중률·컨테이너 CPU 수집(`run_cache_experiment.py`).

### 산출물
- `RUNBOOK.md`(이 파일) · `ANALYSIS.md` · `matrix-results.csv` · `runs/<id>.{json,md,k6.json,k6.txt}` ×12 · `explain-before-after.txt`
- 재현 도구: `load-test/perf/{perf-stack-compose,prometheus-perf,monitoring-perf-compose}.yml · sample-cgroup.sh · run_matrix.py · explain-capture.sh · build_report.py · build_html.py`
- 리포트: `docs/week5/09-load-test-results.html`

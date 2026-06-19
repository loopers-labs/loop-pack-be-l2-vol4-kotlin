# Run: baseline-100-warm

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 100 |
| 웜업 | warm |
| 측정시간 | 190.3s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 92.61303106766941 (목표 100) |
| 총 요청 | 17597 |
| 실패율 | 0.8195715178723646 |
| 응답 med | 9993.291ms |
| 응답 p90 | 9995.475ms |
| 응답 p95 | 9997.055400000001ms |
| 응답 p99 | 10000.294ms |
| 응답 max | 10014.308ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 63.8 | 0.335 | 1429.0 |
| perf-mysql | 690.68 | 3.629 | 3179.6 |
| perf-redis-master | 1.52 | 0.008 | 3.4 |
| perf-redis-readonly | 1.53 | 0.008 | 3.6 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9757 | 1.0 |
| iowait | 0.0001 | 0.0003 |
| PSI cpu waiting Δ(s) | 185.2322 | |
| PSI io waiting Δ(s) | 0.134 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.0849 / 0.2124 |
| JIT 컴파일 Δ(ms) | 21510.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

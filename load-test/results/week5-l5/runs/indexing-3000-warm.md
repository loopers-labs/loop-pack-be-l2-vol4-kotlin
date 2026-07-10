# Run: indexing-3000-warm

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 3000 |
| 웜업 | warm |
| 측정시간 | 182.9s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 937.206286253997 (목표 3000) |
| 총 요청 | 171152 |
| 실패율 | 0 |
| 응답 med | 3221.6895ms |
| 응답 p90 | 4074.2248ms |
| 응답 p95 | 4252.5848ms |
| 응답 p99 | 4666.99282ms |
| 응답 max | 6356.21ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 554.87 | 3.033 | 1454.0 |
| perf-mysql | 166.73 | 0.911 | 3196.2 |
| perf-redis-master | 1.68 | 0.009 | 3.4 |
| perf-redis-readonly | 1.67 | 0.009 | 3.4 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9924 | 0.9989 |
| iowait | 0.0 | 0.0 |
| PSI cpu waiting Δ(s) | 174.9532 | |
| PSI io waiting Δ(s) | 0.1841 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.7556 / 0.7893 |
| JIT 컴파일 Δ(ms) | 358257.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 154.0 |
| Hikari active max | 40.0 |

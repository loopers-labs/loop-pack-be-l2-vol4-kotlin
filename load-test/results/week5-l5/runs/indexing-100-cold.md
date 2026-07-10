# Run: indexing-100-cold

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 100 |
| 웜업 | cold |
| 측정시간 | 180.6s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 99.8986428369776 (목표 100) |
| 총 요청 | 18000 |
| 실패율 | 0 |
| 응답 med | 11.204ms |
| 응답 p90 | 14.361300000000002ms |
| 응답 p95 | 16.549149999999997ms |
| 응답 p99 | 45.058129999999984ms |
| 응답 max | 1111.425ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 121.1 | 0.671 | 1231.6 |
| perf-mysql | 31.47 | 0.174 | 3196.1 |
| perf-redis-master | 1.6 | 0.009 | 3.6 |
| perf-redis-readonly | 1.56 | 0.009 | 3.6 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.3556 | 0.9183 |
| iowait | 0.001 | 0.0013 |
| PSI cpu waiting Δ(s) | 3.5279 | |
| PSI io waiting Δ(s) | 0.2307 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.193 / 1.0 |
| JIT 컴파일 Δ(ms) | 42794.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 0.0 |
| Hikari active max | 30.0 |

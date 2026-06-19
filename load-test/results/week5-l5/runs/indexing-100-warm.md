# Run: indexing-100-warm

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 100 |
| 웜업 | warm |
| 측정시간 | 180.3s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 99.99535299373449 (목표 100) |
| 총 요청 | 18000 |
| 실패율 | 0 |
| 응답 med | 10.707ms |
| 응답 p90 | 12.957ms |
| 응답 p95 | 13.909099999999999ms |
| 응답 p99 | 18.52175999999999ms |
| 응답 max | 132.934ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 85.18 | 0.472 | 1221.1 |
| perf-mysql | 31.45 | 0.174 | 3195.2 |
| perf-redis-master | 1.55 | 0.009 | 3.4 |
| perf-redis-readonly | 1.53 | 0.009 | 3.3 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.2076 | 0.262 |
| iowait | 0.0009 | 0.0012 |
| PSI cpu waiting Δ(s) | 1.1093 | |
| PSI io waiting Δ(s) | 0.1973 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.1203 / 0.2167 |
| JIT 컴파일 Δ(ms) | 17816.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 0.0 |
| Hikari active max | 1.0 |

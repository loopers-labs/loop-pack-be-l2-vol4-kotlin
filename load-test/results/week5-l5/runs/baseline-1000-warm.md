# Run: baseline-1000-warm

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 1000 |
| 웜업 | warm |
| 측정시간 | 420.1s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 131.64874873042652 (목표 1000) |
| 총 요청 | 55257 |
| 실패율 | 0.9919286244276744 |
| 응답 med | 9702.302ms |
| 응답 p90 | 9995.177ms |
| 응답 p95 | 10035.694399999998ms |
| 응답 p99 | 239473.62544ms |
| 응답 max | 240428.316ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 85.85 | 0.204 | 1586.7 |
| perf-mysql | 1047.62 | 2.494 | 3179.6 |
| perf-redis-master | 3.62 | 0.009 | 3.4 |
| perf-redis-readonly | 3.66 | 0.009 | 3.5 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 1.0 | 1.0 |
| iowait | 0.0 | 0.0 |
| PSI cpu waiting Δ(s) | 282.3429 | |
| PSI io waiting Δ(s) | 0.4024 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.1212 / 0.1958 |
| JIT 컴파일 Δ(ms) | 21346.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

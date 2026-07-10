# Run: baseline-3000-cold

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 3000 |
| 웜업 | cold |
| 측정시간 | 190.4s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 287.53773827539817 (목표 3000) |
| 총 요청 | 54626 |
| 실패율 | 0.9927507047925896 |
| 응답 med | 9605.163ms |
| 응답 p90 | 9994.23ms |
| 응답 p95 | 9995.599ms |
| 응답 p99 | 10000.4545ms |
| 응답 max | 10077.652ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 102.12 | 0.536 | 1578.6 |
| perf-mysql | 651.33 | 3.421 | 3180.0 |
| perf-redis-master | 1.51 | 0.008 | 3.5 |
| perf-redis-readonly | 1.54 | 0.008 | 3.4 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9743 | 1.0 |
| iowait | 0.0001 | 0.0002 |
| PSI cpu waiting Δ(s) | 182.4288 | |
| PSI io waiting Δ(s) | 0.2084 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.1467 / 0.5 |
| JIT 컴파일 Δ(ms) | 54320.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

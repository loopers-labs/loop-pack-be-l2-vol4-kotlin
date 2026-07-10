# Run: baseline-1000-cold

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 1000 |
| 웜업 | cold |
| 측정시간 | 190.3s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 287.33716201118915 (목표 1000) |
| 총 요청 | 54592 |
| 실패율 | 0.9928194607268465 |
| 응답 med | 9697.978ms |
| 응답 p90 | 9994.599ms |
| 응답 p95 | 9995.42245ms |
| 응답 p99 | 10000.178ms |
| 응답 max | 10003.909ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 104.62 | 0.55 | 1578.7 |
| perf-mysql | 649.75 | 3.414 | 3179.9 |
| perf-redis-master | 1.55 | 0.008 | 3.3 |
| perf-redis-readonly | 1.55 | 0.008 | 3.3 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9748 | 1.0 |
| iowait | 0.0001 | 0.0004 |
| PSI cpu waiting Δ(s) | 183.7801 | |
| PSI io waiting Δ(s) | 0.1624 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.1628 / 1.0 |
| JIT 컴파일 Δ(ms) | 60142.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

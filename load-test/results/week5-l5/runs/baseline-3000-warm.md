# Run: baseline-3000-warm

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 3000 |
| 웜업 | warm |
| 측정시간 | 190.3s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 285.4308711109708 (목표 3000) |
| 총 요청 | 54219 |
| 실패율 | 1 |
| 응답 med | 9070.23ms |
| 응답 p90 | 9992.2132ms |
| 응답 p95 | 9995.8335ms |
| 응답 p99 | 10000.353ms |
| 응답 max | 10029.014ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 62.61 | 0.329 | 1590.3 |
| perf-mysql | 692.41 | 3.638 | 3180.1 |
| perf-redis-master | 1.46 | 0.008 | 3.4 |
| perf-redis-readonly | 1.51 | 0.008 | 3.2 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 1.0 | 1.0 |
| iowait | 0.0 | 0.0 |
| PSI cpu waiting Δ(s) | 188.6187 | |
| PSI io waiting Δ(s) | 0.1874 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.0828 / 0.2301 |
| JIT 컴파일 Δ(ms) | 18169.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

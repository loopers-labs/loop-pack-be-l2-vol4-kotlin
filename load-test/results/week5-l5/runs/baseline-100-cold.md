# Run: baseline-100-cold

| 조건 | 값 |
|---|---|
| 인덱스 | baseline |
| 목표 TPS | 100 |
| 웜업 | cold |
| 측정시간 | 190.4s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 92.61489598366137 (목표 100) |
| 총 요청 | 17597 |
| 실패율 | 0.9252145252031596 |
| 응답 med | 9993.966ms |
| 응답 p90 | 9995.4174ms |
| 응답 p95 | 9996.0722ms |
| 응답 p99 | 10000.17712ms |
| 응답 max | 10021.806ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 97.7 | 0.513 | 1452.6 |
| perf-mysql | 656.5 | 3.448 | 3180.5 |
| perf-redis-master | 1.51 | 0.008 | 3.4 |
| perf-redis-readonly | 1.5 | 0.008 | 3.3 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9764 | 1.0 |
| iowait | 0.0001 | 0.0005 |
| PSI cpu waiting Δ(s) | 182.681 | |
| PSI io waiting Δ(s) | 0.1754 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.1284 / 0.4163 |
| JIT 컴파일 Δ(ms) | 55990.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 160.0 |
| Hikari active max | 40.0 |

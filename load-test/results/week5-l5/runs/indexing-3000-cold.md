# Run: indexing-3000-cold

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 3000 |
| 웜업 | cold |
| 측정시간 | 184.2s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 617.1209290760756 (목표 3000) |
| 총 요청 | 113430 |
| 실패율 | 0.030335889976196774 |
| 응답 med | 4476.4490000000005ms |
| 응답 p90 | 7032.534000000001ms |
| 응답 p95 | 9032.839049999997ms |
| 응답 p99 | 10000.408ms |
| 응답 max | 10001.675ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 601.95 | 3.268 | 1373.8 |
| perf-mysql | 123.83 | 0.672 | 3196.2 |
| perf-redis-master | 1.65 | 0.009 | 3.9 |
| perf-redis-readonly | 1.68 | 0.009 | 3.2 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.9496 | 0.9991 |
| iowait | 0.0001 | 0.0005 |
| PSI cpu waiting Δ(s) | 175.0417 | |
| PSI io waiting Δ(s) | 0.2029 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.7928 / 0.8898 |
| JIT 컴파일 Δ(ms) | 387640.0 |
| GC pause Δ(s) | 0.704 |
| Hikari pending max | 166.0 |
| Hikari active max | 40.0 |

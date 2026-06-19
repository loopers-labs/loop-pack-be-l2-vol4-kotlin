# Run: indexing-1000-cold

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 1000 |
| 웜업 | cold |
| 측정시간 | 184.4s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 614.5741568502684 (목표 1000) |
| 총 요청 | 113013 |
| 실패율 | 0.006724890056896109 |
| 응답 med | 4487.925ms |
| 응답 p90 | 6903.9202000000005ms |
| 응답 p95 | 8345.118399999998ms |
| 응답 p99 | 9787.87284ms |
| 응답 max | 10013.46ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 603.66 | 3.273 | 1379.3 |
| perf-mysql | 123.19 | 0.668 | 3196.3 |
| perf-redis-master | 1.64 | 0.009 | 3.7 |
| perf-redis-readonly | 1.67 | 0.009 | 3.6 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.8936 | 0.9991 |
| iowait | 0.0002 | 0.0009 |
| PSI cpu waiting Δ(s) | 172.9673 | |
| PSI io waiting Δ(s) | 0.1728 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.7884 / 0.9023 |
| JIT 컴파일 Δ(ms) | 384319.0 |
| GC pause Δ(s) | 1.364 |
| Hikari pending max | 165.0 |
| Hikari active max | 40.0 |

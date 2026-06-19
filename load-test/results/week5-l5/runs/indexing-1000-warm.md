# Run: indexing-1000-warm

| 조건 | 값 |
|---|---|
| 인덱스 | indexing |
| 목표 TPS | 1000 |
| 웜업 | warm |
| 측정시간 | 180.3s |

## k6 (응답·처리량)
| metric | value |
|---|---|
| achieved RPS | 978.2350193593866 (목표 1000) |
| 총 요청 | 176099 |
| 실패율 | 0.0015843360836802025 |
| 응답 med | 12.134ms |
| 응답 p90 | 99.6952ms |
| 응답 p95 | 781.4671999999966ms |
| 응답 p99 | 4191.0689999999995ms |
| 응답 max | 10000.524ms |

## 컨테이너별 CPU/메모리 (cgroup v2 직접)
| 컨테이너 | CPU-초 | 평균 코어 | 메모리MB |
|---|---|---|---|
| perf-app | 361.77 | 2.006 | 1443.7 |
| perf-mysql | 177.13 | 0.982 | 3196.4 |
| perf-redis-master | 1.61 | 0.009 | 4.0 |
| perf-redis-readonly | 1.61 | 0.009 | 3.3 |

## 호스트 (node_exporter)
| metric | avg | max |
|---|---|---|
| CPU busy(0~1) | 0.7614 | 0.816 |
| iowait | 0.0003 | 0.0006 |
| PSI cpu waiting Δ(s) | 84.9399 | |
| PSI io waiting Δ(s) | 0.2017 | |

## JVM (actuator)
| metric | value |
|---|---|
| process_cpu avg/max | 0.5156 / 0.7044 |
| JIT 컴파일 Δ(ms) | 19535.0 |
| GC pause Δ(s) | 0.0 |
| Hikari pending max | 83.0 |
| Hikari active max | 39.0 |

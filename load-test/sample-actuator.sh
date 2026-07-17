#!/bin/bash
# actuator 이중 관측 샘플러 — 5초 간격으로 서버측 지표를 CSV 로 기록한다.
#   R9 확장: 표준 메트릭 세트 v1 — CPU·load·GC·힙·스레드·Hikari 전체·executor·컨슈머 랙·rankings URI 추가.
#   기존 R8 컬럼(orders/products/hikari/tomcat)은 순서 그대로 유지 — 과거 결과 CSV 와 컬럼 비교 가능.
#   api(mgmt 8081)·streamer(mgmt 8084) 각각 1프로세스씩 띄운다. 없는 지표는 0 (api: kafka_lag, streamer: tomcat 등).
#   사용: ACT=http://<host>:<mgmt>/actuator/prometheus ./load-test/sample-actuator.sh <출력파일> <지속초>
OUT="${1:?출력 파일 경로 필요}"
DUR="${2:-300}"
ACT="${ACT:-http://localhost:8081/actuator/prometheus}"
echo "epoch,orders_count,orders_sum,orders_max,products_count,detail_count,hikari_pending,hikari_active,tomcat_busy,rank_count,rank_sum,rank_max,proc_cpu,sys_cpu,load1,heap_used,gc_count,gc_sum,gc_max,threads_live,hikari_idle,hikari_timeout,exec_active,exec_queued,kafka_lag_max" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  M=$(curl -s --max-time 3 "$ACT")
  TS=$(date +%s)
  OC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/orders"' | awk '{s+=$NF} END {printf "%.0f", s}')
  OS=$(echo "$M" | grep 'http_server_requests_seconds_sum'   | grep 'uri="/api/v1/orders"' | awk '{s+=$NF} END {printf "%.3f", s}')
  OM=$(echo "$M" | grep 'http_server_requests_seconds_max'   | grep 'uri="/api/v1/orders"' | awk '{m=$NF>m?$NF:m} END {printf "%.3f", m}')
  PC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/products"' | awk '{s+=$NF} END {printf "%.0f", s}')
  DC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/products/{productId}"' | awk '{s+=$NF} END {printf "%.0f", s}')
  HP=$(echo "$M" | grep '^hikaricp_connections_pending' | awk '{s+=$NF} END {printf "%.0f", s}')
  HA=$(echo "$M" | grep '^hikaricp_connections_active'  | awk '{s+=$NF} END {printf "%.0f", s}')
  TB=$(echo "$M" | grep '^tomcat_threads_busy_threads'  | awk '{s+=$NF} END {printf "%.0f", s}')
  RC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/rankings"' | awk '{s+=$NF} END {printf "%.0f", s}')
  RS=$(echo "$M" | grep 'http_server_requests_seconds_sum'   | grep 'uri="/api/v1/rankings"' | awk '{s+=$NF} END {printf "%.3f", s}')
  RM=$(echo "$M" | grep 'http_server_requests_seconds_max'   | grep 'uri="/api/v1/rankings"' | awk '{m=$NF>m?$NF:m} END {printf "%.3f", m}')
  PU=$(echo "$M" | grep '^process_cpu_usage'        | awk '{printf "%.4f", $NF}')
  SU=$(echo "$M" | grep '^system_cpu_usage'         | awk '{printf "%.4f", $NF}')
  L1=$(echo "$M" | grep '^system_load_average_1m'   | awk '{printf "%.2f", $NF}')
  HU=$(echo "$M" | grep '^jvm_memory_used_bytes{area="heap"' | awk '{s+=$NF} END {printf "%.0f", s}')
  GC=$(echo "$M" | grep '^jvm_gc_pause_seconds_count' | awk '{s+=$NF} END {printf "%.0f", s}')
  GS=$(echo "$M" | grep '^jvm_gc_pause_seconds_sum'   | awk '{s+=$NF} END {printf "%.3f", s}')
  GM=$(echo "$M" | grep '^jvm_gc_pause_seconds_max'   | awk '{m=$NF>m?$NF:m} END {printf "%.3f", m}')
  TL=$(echo "$M" | grep '^jvm_threads_live_threads'   | awk '{printf "%.0f", $NF}')
  HI=$(echo "$M" | grep '^hikaricp_connections_idle'    | awk '{s+=$NF} END {printf "%.0f", s}')
  HT=$(echo "$M" | grep '^hikaricp_connections_timeout' | awk '{s+=$NF} END {printf "%.0f", s}')
  EA=$(echo "$M" | grep '^executor_active_threads'      | awk '{s+=$NF} END {printf "%.0f", s}')
  EQ=$(echo "$M" | grep '^executor_queued_tasks'        | awk '{s+=$NF} END {printf "%.0f", s}')
  KL=$(echo "$M" | grep 'kafka_consumer_fetch_manager_records_lag_max' | awk '{m=$NF>m?$NF:m} END {printf "%.0f", m}')
  echo "$TS,${OC:-0},${OS:-0},${OM:-0},${PC:-0},${DC:-0},${HP:-0},${HA:-0},${TB:-0},${RC:-0},${RS:-0},${RM:-0},${PU:-0},${SU:-0},${L1:-0},${HU:-0},${GC:-0},${GS:-0},${GM:-0},${TL:-0},${HI:-0},${HT:-0},${EA:-0},${EQ:-0},${KL:-0}" >> "$OUT"
  sleep 5
done

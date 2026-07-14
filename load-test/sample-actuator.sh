#!/bin/bash
# EXP-04 — actuator 이중 관측 샘플러. 5초 간격으로 서버측 지표를 CSV 로 기록한다.
#   사용: ./load-test/sample-actuator.sh <출력파일> <지속초>
# 컬럼: epoch,orders_count,orders_sum,orders_max,products_count,hikari_pending,hikari_active,tomcat_busy
OUT="${1:?출력 파일 경로 필요}"
DUR="${2:-300}"
ACT="${ACT:-http://localhost:8081/actuator/prometheus}"
echo "epoch,orders_count,orders_sum,orders_max,products_count,detail_count,hikari_pending,hikari_active,tomcat_busy" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  M=$(curl -s --max-time 3 "$ACT")
  TS=$(date +%s)
  OC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/orders"' | awk '{s+=$2} END {printf "%.0f", s}')
  OS=$(echo "$M" | grep 'http_server_requests_seconds_sum'   | grep 'uri="/api/v1/orders"' | awk '{s+=$2} END {printf "%.3f", s}')
  OM=$(echo "$M" | grep 'http_server_requests_seconds_max'   | grep 'uri="/api/v1/orders"' | awk '{m=$2>m?$2:m} END {printf "%.3f", m}')
  PC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/products"' | awk '{s+=$2} END {printf "%.0f", s}')
  DC=$(echo "$M" | grep 'http_server_requests_seconds_count' | grep 'uri="/api/v1/products/{productId}"' | awk '{s+=$2} END {printf "%.0f", s}')
  HP=$(echo "$M" | grep '^hikaricp_connections_pending' | awk '{s+=$2} END {printf "%.0f", s}')
  HA=$(echo "$M" | grep '^hikaricp_connections_active'  | awk '{s+=$2} END {printf "%.0f", s}')
  TB=$(echo "$M" | grep '^tomcat_threads_busy_threads'  | awk '{s+=$2} END {printf "%.0f", s}')
  echo "$TS,${OC:-0},${OS:-0},${OM:-0},${PC:-0},${DC:-0},${HP:-0},${HA:-0},${TB:-0}" >> "$OUT"
  sleep 5
done

#!/usr/bin/env bash
# EXP-POOL 샘플러 — 한 arm(ramp) 동안 "풀 병목 vs MySQL-CPU 바운드 vs 락 경합" 을 가르는 지표를 CSV 로 채집.
#   로컬 맥에서 실행. 7/16 본런이 MySQL 측 지표를 안 남겨 "병목=MySQL" 을 앱 정황으로만 추론했던 공백을 메운다.
#
#   가르는 축:
#     · hikari_acquire (획득 대기, Δsum/Δcount) ↑  → 스레드가 풀 슬롯 대기 = 풀이 병목 (원인 후보)
#     · hikari_usage   (점유시간,  Δsum/Δcount) ↑  → 커넥션을 오래 쥠 = 다운스트림(MySQL)이 느림
#     · mysqld_cpu 포화 + Threads_running↑         → MySQL CPU/스캔 바운드 (풀 확대 무익)
#     · innodb_row_lock_waits / _time ↑            → 락 경합 (S-READ 는 순수 SELECT=MVCC라 0 이어야 정상)
#   acquire·usage 는 마이크로미터 누적 카운터라 raw sum/count 를 매 틱 기록 → 분석에서 인접 틱 델타로 구간 평균 산출.
#
# usage: ./sample-pool.sh <out.csv> <duration_sec> <interval_sec> <app_pub> <infra_pub> <key.pem>
set -uo pipefail
OUT="$1"; DUR="$2"; IV="$3"; APP="$4"; INFRA="$5"; KEY="$6"
SSH=(ssh -i "$KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=5 -o UserKnownHostsFile=/dev/null)

echo "epoch,hikari_active,hikari_pending,hikari_idle,hikari_total,hikari_max,acquire_sum,acquire_count,usage_sum,usage_count,timeout_total,mysql_threads_running,mysql_threads_connected,innodb_row_lock_cur_waits,innodb_row_lock_waits,innodb_row_lock_time_ms,mysql_queries,mysqld_cpu_pct" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  T=$(date +%s)
  P=$(curl -s -m 4 "http://$APP:8081/actuator/prometheus" || true)
  g() { printf '%s\n' "$P" | awk -v pat="$1" '$0 ~ pat {print $2; exit}'; }
  HA=$(g '^hikaricp_connections_active\{')
  HP=$(g '^hikaricp_connections_pending\{')
  HI=$(g '^hikaricp_connections_idle\{')
  HT=$(g '^hikaricp_connections\{')
  HM=$(g '^hikaricp_connections_max\{')
  AS=$(g '^hikaricp_connections_acquire_seconds_sum\{')
  AC=$(g '^hikaricp_connections_acquire_seconds_count\{')
  US=$(g '^hikaricp_connections_usage_seconds_sum\{')
  UC=$(g '^hikaricp_connections_usage_seconds_count\{')
  TO=$(g '^hikaricp_connections_timeout_total\{')
  # MySQL 상태 + mysqld 컨테이너 CPU% 를 한 번의 ssh 로 수집
  M=$("${SSH[@]}" ec2-user@"$INFRA" \
      'sudo docker exec mysql mysql -uroot -proot -N -e "SHOW GLOBAL STATUS WHERE Variable_name IN (\"Threads_running\",\"Threads_connected\",\"Innodb_row_lock_current_waits\",\"Innodb_row_lock_waits\",\"Innodb_row_lock_time\",\"Queries\")"; sudo docker stats --no-stream --format "{{.CPUPerc}}" mysql' \
      2>/dev/null || true)
  m() { printf '%s\n' "$M" | awk -v k="$1" '$1==k {print $2; exit}'; }
  TR=$(m Threads_running); TC=$(m Threads_connected)
  LCW=$(m Innodb_row_lock_current_waits); LW=$(m Innodb_row_lock_waits); LT=$(m Innodb_row_lock_time); Q=$(m Queries)
  CPU=$(printf '%s\n' "$M" | grep -oE '[0-9.]+%' | tr -d '%' | tail -1)
  echo "$T,${HA:-},${HP:-},${HI:-},${HT:-},${HM:-},${AS:-},${AC:-},${US:-},${UC:-},${TO:-},${TR:-},${TC:-},${LCW:-},${LW:-},${LT:-},${Q:-},${CPU:-}" >> "$OUT"
  sleep "$IV"
done
echo "샘플 완료 → $OUT ($(( $(wc -l < "$OUT") - 1 )) 틱)"

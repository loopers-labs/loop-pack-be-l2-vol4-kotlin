#!/usr/bin/env bash
# EXP-POOL — "Hikari 40/40 소진과 함께 온 읽기 천장이 풀 원인인가 MySQL 증상인가" 판별 실험 (로컬 오케스트레이터).
#   목적: MySQL 버전 천장의 원인을 실측 확정해 Stage 1↔3 비교표·PR 서사를 강화. MySQL 튜닝으로 우회하는 게 아님.
#   변인: maximum-pool-size ∈ {40(현행 재현), 80(사용자 가설), 10(HikariCP small-pool 역검증)}.
#   고정: R8 2노드, ranking-read.js (warmup 20/s×1m + ramp 50→100→200→400/s 각 1m), 같은 시드(201k 랭킹행).
#   판정: 천장 rps·p95(k6) ↔ mysqld CPU·Threads_running(sampler) 로 원인/증상을 가른다.
#     · 천장 불변 + MySQL CPU 포화 + Threads_running↑ → 풀=증상 (예상)
#     · 천장 상승 + 구천장 시 MySQL 여유          → 풀=원인 (가설 성립)
#     · 80 에서 천장 하락/p95 악화                 → 과포화 해악 (공식 위키 주장 실증)
# usage: APP=<pub> INFRA_PUB=<pub> INFRA_PRIV=<priv> KEY=<pem> [POOLS="40 80 10"] ./run-pool-arms.sh
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"  # 백그라운드 비로그인 셸에서 k6 경로 확보
APP="${APP:?APP=<app public IP>}"; INFRA_PUB="${INFRA_PUB:?}"; INFRA_PRIV="${INFRA_PRIV:?}"; KEY="${KEY:?}"
POOLS="${POOLS:-40 80 10}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
K6="$REPO/load-test/k6/ranking-read.js"
OUTDIR="$REPO/load-test/results/exp-pool"; mkdir -p "$OUTDIR"
SSH=(ssh -i "$KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=8 -o UserKnownHostsFile=/dev/null)

for POOL in $POOLS; do
  echo "════════════════════ ARM pool=$POOL ════════════════════"
  # 1) 앱 재시작 (풀 오버라이드) — start-api.sh 가 pkill 선행
  "${SSH[@]}" ec2-user@"$APP" "POOL=$POOL INFRA=$INFRA_PRIV ~/start-api.sh"
  # 2) 헬스 UP 대기 (최대 ~2분)
  UP=""
  for i in $(seq 1 40); do
    if curl -s -m 4 "http://$APP:8081/actuator/health" | grep -q '"status":"UP"'; then UP=1; echo "  health UP (${i}회차)"; break; fi
    sleep 3
  done
  [ -z "$UP" ] && { echo "  !! 헬스 UP 실패 — arm 중단"; "${SSH[@]}" ec2-user@"$APP" 'tail -30 ~/api.log'; exit 1; }
  # 3) 실제 적용된 풀 크기 검증 (기대 = $POOL)
  #    curl 출력을 변수로 받은 뒤 awk — 파이프 + awk exit 는 SIGPIPE 로 curl 을 죽여 pipefail 이 스크립트를 중단시킴.
  PROM="$(curl -s -m 4 "http://$APP:8081/actuator/prometheus" || true)"
  echo "  적용된 hikari max = $(printf '%s\n' "$PROM" | awk '/^hikaricp_connections_max\{/{print $2; exit}')"
  # 4) 버퍼풀·JIT 워밍업 (constant 20/s × 1m)
  echo "  [warmup]"; k6 run -e MODE=warmup -e BASE_URL="http://$APP:8080" "$K6" > "$OUTDIR/warmup-$POOL.log" 2>&1 || true
  # 5) 샘플러 백그라운드 (ramp 4m + slack = 300s, 15s 간격)
  "$HERE/sample-pool.sh" "$OUTDIR/sample-$POOL.csv" 300 15 "$APP" "$INFRA_PUB" "$KEY" &
  SPID=$!
  # 6) S-READ ramp — k6 summary 저장
  echo "  [ramp]"; k6 run -e BASE_URL="http://$APP:8080" --summary-export="$OUTDIR/read-$POOL.json" "$K6" 2>&1 | tee "$OUTDIR/read-$POOL.log"
  wait "$SPID" || true
  echo "  ARM $POOL 완료 → read-$POOL.json / sample-$POOL.csv"
done
echo "전체 완료 → $OUTDIR"

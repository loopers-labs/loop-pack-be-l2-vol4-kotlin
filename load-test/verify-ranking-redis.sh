#!/usr/bin/env bash
# R9 Stage 3 랭킹 정산(reconciliation) — 성능 런 종료 + 컨슈머 랙 0 도달 후 실행한다.
#   기대값 E = 0.1·V + 0.2·(L+ − L−) + 0.7·O  (V·L·O 는 k6 커스텀 카운터 출력에서)
#   ⚠️ score 가 IEEE 754 double 누적이라 SQL판과 달리 오차 0 이 아니다 — ε(기본 1e-6·상대) 비교.
#   carry 검증 대상: 23:50 스냅샷(오늘×0.1) + 00:00 tail 병합 후 내일/오늘 합 비율 ≈ 0.1.
#   METRICS 교차 대사(product_metrics·event_handled)는 SQL 그대로 → verify-ranking.sql [3][4] 재사용.
#   사용: ./verify-ranking-redis.sh   (읽기 = replica 기본, REDIS_PORT=6379 로 master 전환 가능)

set -euo pipefail

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6380}"   # 정산 읽기 = replica (서빙 경로와 동일)

TODAY=$(TZ=Asia/Seoul date +%Y%m%d)
TOMORROW=$(TZ=Asia/Seoul date -v+1d +%Y%m%d 2>/dev/null || TZ=Asia/Seoul date -d '+1 day' +%Y%m%d)
KEY_TODAY="ranking:all:${TODAY}"
KEY_TOMORROW="ranking:all:${TOMORROW}"

rcli() { redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "$@"; }

SUM_LUA='local s = 0
local t = redis.call("ZRANGE", KEYS[1], 0, -1, "WITHSCORES")
for i = 2, #t, 2 do s = s + tonumber(t[i]) end
return tostring(s)'

zsum() { rcli EVAL "$SUM_LUA" 1 "$1"; }

today_sum=$(zsum "$KEY_TODAY")
tomorrow_sum=$(zsum "$KEY_TOMORROW")

# [1] 오늘 키 — 기대값 E 와 대사 (ε 비교)
echo "today_sum        $today_sum   (rows=$(rcli ZCARD "$KEY_TODAY"))"

# [2] 내일 키 — 스냅샷+tail 병합 결과
echo "tomorrow_sum     $tomorrow_sum   (rows=$(rcli ZCARD "$KEY_TOMORROW"))"

# [2-1] carry 비율 — 1 회 런 기준 0.1 수렴 (double ε 허용)
awk -v t="$today_sum" -v n="$tomorrow_sum" \
  'BEGIN { if (t > 0) printf "carry_ratio      %.6f\n", n / t; else print "carry_ratio      n/a (today_sum=0)" }'

# [4-Redis] RANKING 멱등 장부 — handled 키 수. k6 발행 성공 수와 대사 (D1-B: 장부가 Redis)
handled=$(rcli --scan --pattern 'ranking:handled:*' | wc -l | tr -d ' ')
echo "handled_keys     $handled"

# [5] 이상 키 스캔 — 오늘·내일 외 ranking:all:* (TTL 만료 대기 중인 전일 키만 정상)
echo "--- other ranking keys ---"
rcli --scan --pattern 'ranking:all:*' | grep -v -e "$TODAY" -e "$TOMORROW" || echo "(none)"

# [6] 상위 20 스냅샷 — Stage 1 실측(verify-ranking.sql [6])과 대조용으로 결과 보관
echo "--- top 20 ($KEY_TODAY) ---"
rcli ZRANGE "$KEY_TODAY" 0 19 REV WITHSCORES

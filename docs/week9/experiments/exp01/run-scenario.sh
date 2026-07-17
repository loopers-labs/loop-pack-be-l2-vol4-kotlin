#!/usr/bin/env bash
# EXP-01 시나리오 실행기 — usage: ./run-scenario.sh {A|B|0} <결과파일prefix>
# slap(30k 쿼리, conc32) 창 안에서 t+5s에 배치 실행 + 배치 전후 단발 쿼리 지연 샘플링
set -euo pipefail
SCENARIO="$1"; PREFIX="$2"
DIR="$(cd "$(dirname "$0")" && pwd)"
Q="SELECT product_id, score FROM product_ranking_daily WHERE ranking_date = CURDATE() ORDER BY score DESC LIMIT 20"

reseed() {
  docker exec docker-mysql-1 mysql -uroot -proot -e "TRUNCATE ranking_exp.product_ranking_daily" 2>/dev/null
  docker exec -i docker-mysql-1 mysql -uroot -proot < "$DIR/02-seed.sql" 2>/dev/null
}

sampler() { # 60 샘플, 샘플당 단발 쿼리 왕복 ms 기록
  for i in $(seq 1 60); do
    S=$(date +%s%N)
    docker exec docker-mysql-1 mysql -uexp -pexp -e "$Q" ranking_exp >/dev/null 2>&1
    E=$(date +%s%N)
    echo "$(date +%H:%M:%S.%3N) $(( (E - S) / 1000000 ))ms"
  done > "$PREFIX-sampler.txt"
}

echo "[reseed]"; reseed
echo "[slap 시작 — 30k q · conc32]"
docker run --rm --network docker_default mariadb:11 mariadb-slap \
  --host=docker-mysql-1 -uexp -pexp --skip-ssl \
  --create-schema=ranking_exp --no-drop \
  --concurrency=32 --number-of-queries=30000 \
  --query="$Q" > "$PREFIX-slap.txt" 2>&1 &
SLAP_PID=$!

sampler & SAMPLER_PID=$!
sleep 5

case "$SCENARIO" in
  A) echo "[배치 A — 제자리 전면 UPDATE ×0.1]"
     BS=$(date +%s%N)
     docker exec -i docker-mysql-1 mysql -uroot -proot < "$DIR/03-batch-a-decay.sql" 2>/dev/null
     BE=$(date +%s%N); echo "batch_ms=$(( (BE - BS) / 1000000 ))" > "$PREFIX-batch.txt" ;;
  B) echo "[배치 B — 사전 시딩 INSERT ×0.1]"
     BS=$(date +%s%N)
     docker exec -i docker-mysql-1 mysql -uroot -proot < "$DIR/04-batch-b-carryover.sql" 2>/dev/null
     BE=$(date +%s%N); echo "batch_ms=$(( (BE - BS) / 1000000 ))" > "$PREFIX-batch.txt" ;;
  0) echo "[배치 없음 — baseline]"; echo "batch_ms=0" > "$PREFIX-batch.txt" ;;
esac

wait $SLAP_PID $SAMPLER_PID
echo "== slap:"; grep -E "Average|Minimum" "$PREFIX-slap.txt" | head -2
echo "== batch:"; cat "$PREFIX-batch.txt"
echo "== sampler(ms) 분포:"; awk '{gsub("ms","",$2); print $2}' "$PREFIX-sampler.txt" | sort -n | awk '{a[NR]=$1} END{print "min="a[1], "p50="a[int(NR*0.5)], "p95="a[int(NR*0.95)], "max="a[NR]}'

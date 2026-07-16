#!/usr/bin/env bash
# EXP-02 (Stage 1a) 실행기 — 조회 시점 계산의 비용 실측
# usage: ./run-stage1a.sh <결과디렉터리>
# 측정: ① EXPLAIN / EXPLAIN ANALYZE (플랜·단건 실행시간) ② mariadb-slap conc32 (처리량)
# 대조군: exp01 product_ranking_daily (쓰기 시점 score + 인덱스 리드) — 같은 환경에서 재측정
set -euo pipefail
OUT="$1"; mkdir -p "$OUT"
DIR="$(cd "$(dirname "$0")" && pwd)"

SCORE_10K="0.1*view_count + 0.2*like_count + 0.6*sales_score"
TOP20_10K="SELECT product_id, ${SCORE_10K} AS score FROM product_metrics_10k ORDER BY score DESC LIMIT 20"
TOP20_100K="SELECT product_id, ${SCORE_10K} AS score FROM product_metrics_100k ORDER BY score DESC LIMIT 20"
RANK_10K="SELECT 1 + COUNT(*) AS ranking FROM product_metrics_10k WHERE ${SCORE_10K} > (SELECT ${SCORE_10K} FROM product_metrics_10k WHERE product_id = 5000)"
RANK_100K="SELECT 1 + COUNT(*) AS ranking FROM product_metrics_100k WHERE ${SCORE_10K} > (SELECT ${SCORE_10K} FROM product_metrics_100k WHERE product_id = 50000)"
IDX_TOP20="SELECT product_id, score FROM product_ranking_daily WHERE ranking_date = CURDATE() ORDER BY score DESC LIMIT 20"
IDX_RANK="SELECT 1 + COUNT(*) AS ranking FROM product_ranking_daily WHERE ranking_date = CURDATE() AND score > (SELECT score FROM product_ranking_daily WHERE ranking_date = CURDATE() AND product_id = 50000)"

mysql_root() { docker exec -i docker-mysql-1 mysql -uroot -proot ranking_exp "$@"; }

explain_pair() { # $1=이름 $2=쿼리
  { echo "-- EXPLAIN: $1"; mysql_root -e "EXPLAIN $2";
    echo; echo "-- EXPLAIN ANALYZE: $1"; mysql_root -e "EXPLAIN ANALYZE $2"; } > "$OUT/explain-$1.txt" 2>&1
}

slap() { # $1=이름 $2=쿼리 $3=쿼리수
  docker run --rm --network docker_default mariadb:11 mariadb-slap \
    --host=docker-mysql-1 -uexp -pexp --skip-ssl \
    --create-schema=ranking_exp --no-drop \
    --concurrency=32 --number-of-queries="$3" \
    --query="$2" > "$OUT/slap-$1.txt" 2>&1
  local avg
  avg=$(grep "Average" "$OUT/slap-$1.txt" | grep -oE '[0-9]+\.[0-9]+')
  echo "$1 n=$3 total=${avg}s qps=$(python3 -c "print(round($3/$avg))")"
}

echo "[1/4] 시드 — exp02 테이블(1만/10만) + 대조군(exp01) 재시드"
mysql_root < "$DIR/01-schema-seed.sql"
mysql_root -e "TRUNCATE product_ranking_daily"
mysql_root < "$DIR/../exp01/02-seed.sql"

echo "[2/4] 워밍업 (버퍼풀)"
for q in "$TOP20_10K" "$TOP20_100K" "$RANK_10K" "$RANK_100K" "$IDX_TOP20" "$IDX_RANK"; do
  for i in 1 2 3; do mysql_root -e "$q" > /dev/null; done
done

echo "[3/4] EXPLAIN / EXPLAIN ANALYZE"
explain_pair top20-10k   "$TOP20_10K"
explain_pair top20-100k  "$TOP20_100K"
explain_pair rank-10k    "$RANK_10K"
explain_pair rank-100k   "$RANK_100K"
explain_pair idx-top20   "$IDX_TOP20"
explain_pair idx-rank    "$IDX_RANK"

echo "[4/4] slap conc32 — 처리량"
{
  slap top20-10k  "$TOP20_10K"  30000
  slap top20-100k "$TOP20_100K" 6000
  slap rank-100k  "$RANK_100K"  6000
  slap idx-top20  "$IDX_TOP20"  30000
  slap idx-rank   "$IDX_RANK"   30000
} | tee "$OUT/summary.txt"

echo "완료 — 결과: $OUT"

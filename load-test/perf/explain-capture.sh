#!/usr/bin/env bash
# ⚠️ matrix 종료 후에만 실행 (EXPLAIN ANALYZE 는 쿼리를 실제 실행 → 매트릭스 중 실행 시 측정 오염).
# baseline / indexing 두 상태에서 목록 쿼리의 실행계획·실측시간을 캡처해 filesort→인덱스 구조 변화를 증명한다.
# 사용(N100): bash ~/perf/explain-capture.sh
set -uo pipefail
M="docker exec perf-mysql mysql -uapplication -papplication loopers"

# 대표 keyset 깊은 페이지 커서: 멱법칙 중위권 근처 (like_count≈30 부근, id 임의)
LC=30
ID=50000

q_global="SELECT * FROM product WHERE status<>'DELETED' ORDER BY like_count DESC, id DESC LIMIT 20"
q_brand="SELECT * FROM product WHERE status<>'DELETED' AND brand_id=3 ORDER BY like_count DESC, id DESC LIMIT 20"
q_keyset="SELECT * FROM product WHERE status<>'DELETED' AND (like_count<$LC OR (like_count=$LC AND id<$ID)) ORDER BY like_count DESC, id DESC LIMIT 20"

set_state() {
  $M -e "DROP INDEX idx_p_lc_id ON product"       >/dev/null 2>&1
  $M -e "DROP INDEX idx_p_brand_lc_id ON product" >/dev/null 2>&1
  if [ "$1" = "indexing" ]; then
    $M -e "CREATE INDEX idx_p_lc_id ON product(like_count, id)"
    $M -e "CREATE INDEX idx_p_brand_lc_id ON product(brand_id, like_count, id)"
  fi
}

capture() {
  local state="$1"
  set_state "$state"
  local idx; idx=$($M -N -e "SELECT GROUP_CONCAT(DISTINCT INDEX_NAME) FROM information_schema.statistics WHERE table_schema='loopers' AND table_name='product'")
  echo "########################################################"
  echo "##### STATE = $state    (product indexes: $idx)"
  echo "########################################################"
  for pair in "Q1_글로벌_첫페이지|$q_global" "Q2_브랜드필터_첫페이지|$q_brand" "Q3_keyset_깊은페이지|$q_keyset"; do
    local label="${pair%%|*}"; local sql="${pair#*|}"
    echo ""; echo "### $label"
    echo "--- EXPLAIN (TREE) ---"
    $M -e "EXPLAIN FORMAT=TREE $sql\G" 2>/dev/null
    # 워밍업 1회 후 2nd run 실측
    $M -e "EXPLAIN ANALYZE $sql\G" >/dev/null 2>&1
    echo "--- EXPLAIN ANALYZE (2nd run 실측) ---"
    $M -e "EXPLAIN ANALYZE $sql\G" 2>/dev/null
  done
}

echo "===== EXPLAIN 전후 비교  $(date -u +%FT%TZ) ====="
capture baseline
echo ""; echo ""
capture indexing
echo ""
echo "===== 완료. 측정용 인덱스 상태는 indexing 으로 종료됨 ====="

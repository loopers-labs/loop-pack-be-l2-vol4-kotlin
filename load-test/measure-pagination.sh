#!/usr/bin/env bash
# Week5 페이지네이션 측정 — OFFSET vs Keyset × 방식(B/A-정렬/A-표시) × 깊이 × 시나리오 × 인덱스 전/후
# 각 케이스 EXPLAIN ANALYZE 워밍업 1회 + 측정 1회(2nd run). 결과는 MEASURE| 라인으로 출력.
set -u
DC="docker exec -i docker-mysql-1 mysql -uapplication -papplication -N loopers"
PLANS=/tmp/perf_plans.txt
: > "$PLANS"

run() { echo "$1" | $DC 2>/dev/null; }

# label, sql(EXPLAIN ANALYZE ...). prints: MEASURE|label|time_ms|method
measure() {
  local label="$1" sql="$2"
  run "$sql" >/dev/null 2>&1                      # warmup
  local out; out="$(run "$sql")"                  # measured
  local root t method
  root="$(printf '%s\n' "$out" | head -1)"
  t="$(printf '%s' "$root" | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' | head -1 | sed -E 's/.*\.\.//')"
  method="$(printf '%s\n' "$out" | grep -oE 'Table scan on [a-z_]+|Covering index [a-z]+ ?(scan|lookup)?|Index range scan on [a-z_]+ using [a-zA-Z_]+|Backward index scan|Sort|Aggregate|Index scan on [a-z_]+ using [a-zA-Z_]+|Index lookup on [a-z_]+ using [a-zA-Z_]+' | sort -u | tr '\n' ',' | sed 's/,$//')"
  echo "MEASURE|$label|${t:-NA}ms|$method"
  { echo "==== $label ===="; printf '%s\n' "$out"; echo; } >> "$PLANS"
}

# keyset 커서 경계(@lc,@id)를 depth N 행으로 잡고 EXPLAIN ANALYZE. (brand 절은 $3)
ks_sql() {  # depth, extra_where(브랜드 등, 없으면 빈문자)
  local N="$1" extra="$2"
  if [ "$N" -eq 0 ]; then
    echo "EXPLAIN ANALYZE SELECT * FROM product WHERE status<>'DELETED' $extra ORDER BY like_count DESC, id DESC LIMIT 10;"
  else
    echo "SET @lc=(SELECT like_count FROM product WHERE status<>'DELETED' $extra ORDER BY like_count DESC,id DESC LIMIT $N,1);
SET @id=(SELECT id FROM product WHERE status<>'DELETED' $extra ORDER BY like_count DESC,id DESC LIMIT $N,1);
EXPLAIN ANALYZE SELECT * FROM product WHERE status<>'DELETED' $extra AND (like_count<@lc OR (like_count=@lc AND id<@id)) ORDER BY like_count DESC, id DESC LIMIT 10;"
  fi
}
off_sql() {  # depth, extra_where
  local N="$1" extra="$2"
  echo "EXPLAIN ANALYZE SELECT * FROM product WHERE status<>'DELETED' $extra ORDER BY like_count DESC, id DESC LIMIT 10 OFFSET $N;"
}

echo "### 사전 확인: 시나리오 모집단"
run "SELECT CONCAT('brand1(mega)=', (SELECT COUNT(*) FROM product WHERE brand_id=1),
        ' | brand500(small)=', (SELECT COUNT(*) FROM product WHERE brand_id=500),
        ' | total=', (SELECT COUNT(*) FROM product));"

# 인덱스 초기화 (멱등)
$DC -e "DROP INDEX idx_p_lc_id ON product" 2>/dev/null
$DC -e "DROP INDEX idx_p_brand_lc_id ON product" 2>/dev/null
$DC -e "DROP INDEX idx_pl_pid ON product_like" 2>/dev/null

echo ""
echo "============================================================"
echo "PHASE 0 — 인덱스 없음 (베이스라인, PK only)"
echo "============================================================"
for N in 0 1000 10000 100000; do measure "B|S1전체|OFFSET|depth=$N|noidx" "$(off_sql $N '')"; done
for N in 0 1000 10000 100000; do measure "B|S1전체|KEYSET|depth=$N|noidx" "$(ks_sql $N '')"; done
for N in 0 1000 5000;          do measure "B|S2대형(brand1)|OFFSET|depth=$N|noidx" "$(off_sql $N 'AND brand_id=1')"; done
for N in 0 1000 5000;          do measure "B|S2대형(brand1)|KEYSET|depth=$N|noidx" "$(ks_sql $N 'AND brand_id=1')"; done
for N in 0 20;                 do measure "B|S3소형(brand500)|OFFSET|depth=$N|noidx" "$(off_sql $N 'AND brand_id=500')"; done
for N in 0 20;                 do measure "B|S3소형(brand500)|KEYSET|depth=$N|noidx" "$(ks_sql $N 'AND brand_id=500')"; done

# 방식 A-정렬용: COUNT 집계로 정렬 (LIMIT 단축 불가)
for N in 0 1000; do
  measure "A정렬|S1전체|OFFSET|depth=$N|noidx" \
  "EXPLAIN ANALYZE SELECT p.id, COUNT(pl.product_id) lc FROM product p LEFT JOIN product_like pl ON pl.product_id=p.id WHERE p.status<>'DELETED' GROUP BY p.id ORDER BY lc DESC, p.id DESC LIMIT 10 OFFSET $N;"
done
# 방식 A-표시용: 페이지 10개 id만 COUNT (정렬엔 안 씀)
measure "A표시|핫10개COUNT|-|-|noidx" \
  "EXPLAIN ANALYZE SELECT pl.product_id, COUNT(*) FROM product_like pl WHERE pl.product_id IN (SELECT id FROM product WHERE status<>'DELETED' ORDER BY like_count DESC, id DESC LIMIT 10) GROUP BY pl.product_id;"
measure "A표시|롱테일10개COUNT|-|-|noidx" \
  "EXPLAIN ANALYZE SELECT pl.product_id, COUNT(*) FROM product_like pl WHERE pl.product_id IN (SELECT id FROM product WHERE status<>'DELETED' ORDER BY id DESC LIMIT 10) GROUP BY pl.product_id;"

echo ""
echo "============================================================"
echo "PHASE 1 — CREATE INDEX product(like_count, id)"
echo "============================================================"
$DC -e "CREATE INDEX idx_p_lc_id ON product(like_count, id)" 2>/dev/null
for N in 0 1000 10000 100000; do measure "B|S1전체|OFFSET|depth=$N|+lc_id" "$(off_sql $N '')"; done
for N in 0 1000 10000 100000; do measure "B|S1전체|KEYSET|depth=$N|+lc_id" "$(ks_sql $N '')"; done

echo ""
echo "============================================================"
echo "PHASE 2 — CREATE INDEX product(brand_id, like_count, id)"
echo "============================================================"
$DC -e "CREATE INDEX idx_p_brand_lc_id ON product(brand_id, like_count, id)" 2>/dev/null
for N in 0 1000 5000; do measure "B|S2대형(brand1)|OFFSET|depth=$N|+brand_lc_id" "$(off_sql $N 'AND brand_id=1')"; done
for N in 0 1000 5000; do measure "B|S2대형(brand1)|KEYSET|depth=$N|+brand_lc_id" "$(ks_sql $N 'AND brand_id=1')"; done
for N in 0 20;        do measure "B|S3소형(brand500)|OFFSET|depth=$N|+brand_lc_id" "$(off_sql $N 'AND brand_id=500')"; done
for N in 0 20;        do measure "B|S3소형(brand500)|KEYSET|depth=$N|+brand_lc_id" "$(ks_sql $N 'AND brand_id=500')"; done

echo ""
echo "============================================================"
echo "PHASE 3 — CREATE INDEX product_like(product_id)  (방식 A용)"
echo "============================================================"
$DC -e "CREATE INDEX idx_pl_pid ON product_like(product_id)" 2>/dev/null
for N in 0 1000; do
  measure "A정렬|S1전체|OFFSET|depth=$N|+pl_pid" \
  "EXPLAIN ANALYZE SELECT p.id, COUNT(pl.product_id) lc FROM product p LEFT JOIN product_like pl ON pl.product_id=p.id WHERE p.status<>'DELETED' GROUP BY p.id ORDER BY lc DESC, p.id DESC LIMIT 10 OFFSET $N;"
done
measure "A표시|핫10개COUNT|-|-|+pl_pid" \
  "EXPLAIN ANALYZE SELECT pl.product_id, COUNT(*) FROM product_like pl WHERE pl.product_id IN (SELECT id FROM product WHERE status<>'DELETED' ORDER BY like_count DESC, id DESC LIMIT 10) GROUP BY pl.product_id;"
measure "A표시|롱테일10개COUNT|-|-|+pl_pid" \
  "EXPLAIN ANALYZE SELECT pl.product_id, COUNT(*) FROM product_like pl WHERE pl.product_id IN (SELECT id FROM product WHERE status<>'DELETED' ORDER BY id DESC LIMIT 10) GROUP BY pl.product_id;"

echo ""
echo "### 최종 인덱스 상태"
run "SELECT DISTINCT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) cols
     FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='loopers' AND TABLE_NAME IN ('product','product_like')
     GROUP BY INDEX_NAME ORDER BY INDEX_NAME;"
echo "DONE"

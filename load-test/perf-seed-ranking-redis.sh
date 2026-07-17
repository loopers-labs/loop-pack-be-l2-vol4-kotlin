#!/usr/bin/env bash
# perf-seed-ranking-redis.sh · Stage 3 랭킹 ZSET 시드 — 멱법칙 분포 (perf-seed-ranking.sql 동치)
# ----------------------------------------------------------------------------
# score = like_count × 0.2 + CRC32 노이즈(0~9.9, 동점 분산) — SQL판과 동일 식이라
#   Stage 1 실측과 같은 분포에서 1:1 대조가 성립한다.
# 내일 키 = 오늘 키 × 0.1 (23:50 스냅샷 직후 상태 재현 — 경계 조회 ?date=내일 대비)
# TTL 은 앱과 동일하게 "키 날짜 +2일 자정 KST" EXPIREAT.
# 선행: perf-seed-l5.sql (product 적재). 1만 스케일 런은 SCALE_WHERE="WHERE id <= 10000".
# 실행: ./perf-seed-ranking-redis.sh   (환경변수: MYSQL_HOST/REDIS_HOST 등 아래 기본값)

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-application}"
MYSQL_PASS="${MYSQL_PASS:-application}"
MYSQL_DB="${MYSQL_DB:-loopers}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"   # 쓰기 = master
SCALE_WHERE="${SCALE_WHERE:-}"

TODAY=$(TZ=Asia/Seoul date +%Y%m%d)
TOMORROW=$(TZ=Asia/Seoul date -v+1d +%Y%m%d 2>/dev/null || TZ=Asia/Seoul date -d '+1 day' +%Y%m%d)
KEY_TODAY="ranking:all:${TODAY}"
KEY_TOMORROW="ranking:all:${TOMORROW}"
# EXPIREAT = 키 날짜 +2일 자정 KST
EXP_TODAY=$(TZ=Asia/Seoul date -j -f %Y%m%d -v+2d "${TODAY}" +%s 2>/dev/null || TZ=Asia/Seoul date -d "${TODAY} +2 days" +%s)
EXP_TOMORROW=$(TZ=Asia/Seoul date -j -f %Y%m%d -v+2d "${TOMORROW}" +%s 2>/dev/null || TZ=Asia/Seoul date -d "${TOMORROW} +2 days" +%s)

redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" DEL "$KEY_TODAY" "$KEY_TOMORROW" > /dev/null

# SQL판과 동일 분포를 MySQL 에서 계산해 ZADD 인라인 커맨드로 변환 → --pipe 대량 적재
mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASS" -N -B "$MYSQL_DB" -e \
  "SELECT id, ROUND(like_count * 0.2 + (CRC32(id) % 100) / 10, 4) FROM product ${SCALE_WHERE};" |
  awk -v k1="$KEY_TODAY" -v k2="$KEY_TOMORROW" \
    '{ printf "ZADD %s %s %s\nZADD %s %.4f %s\n", k1, $2, $1, k2, $2 * 0.1, $1 }' |
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --pipe

redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" EXPIREAT "$KEY_TODAY" "$EXP_TODAY" > /dev/null
redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" EXPIREAT "$KEY_TOMORROW" "$EXP_TOMORROW" > /dev/null

echo "--- seed summary ---"
for key in "$KEY_TODAY" "$KEY_TOMORROW"; do
  card=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ZCARD "$key")
  ttl=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" TTL "$key")
  echo "$key cardinality=$card ttl=${ttl}s"
done

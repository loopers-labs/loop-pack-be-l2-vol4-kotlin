#!/bin/bash
# Redis INFO 샘플러 — 5초 간격 CSV. infra 노드에서 실행한다 (6379는 SG 로 외부 미개방 → docker exec 경유).
#   즉시 정지 신호: evicted_keys > 0 (랭킹 키 축출 = 점수 유실, 런 무효)
#   사용: ./load-test/sample-redis.sh <출력파일> <지속초>   (REDIS_CONTAINER 기본 redis-master)
OUT="${1:?출력 파일 경로 필요}"
DUR="${2:-300}"
C="${REDIS_CONTAINER:-redis-master}"
echo "epoch,used_memory,frag_ratio,ops_per_sec,keyspace_hits,keyspace_misses,expired_keys,evicted_keys,connected_clients,blocked_clients" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  I=$(docker exec "$C" redis-cli INFO 2>/dev/null | tr -d '\r')
  TS=$(date +%s)
  v() { echo "$I" | awk -F: -v k="$1" '$1==k{print $2}'; }
  echo "$TS,$(v used_memory),$(v mem_fragmentation_ratio),$(v instantaneous_ops_per_sec),$(v keyspace_hits),$(v keyspace_misses),$(v expired_keys),$(v evicted_keys),$(v connected_clients),$(v blocked_clients)" >> "$OUT"
  sleep 5
done

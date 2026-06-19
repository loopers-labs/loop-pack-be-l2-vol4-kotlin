#!/usr/bin/env bash
# N100 에서 실행. 컨테이너별 cgroup v2 cpu.stat + memory.current 스냅샷.
# Docker 29 containerd 스냅샷터로 cadvisor 컨테이너 디스커버리가 깨져, cgroup 을 직접 읽는다(원천 동일).
# 출력 컬럼: name usage_usec throttled_usec nr_throttled mem_bytes
for c in perf-app perf-mysql perf-redis-master perf-redis-readonly; do
  id=$(docker inspect --format '{{.Id}}' "$c" 2>/dev/null) || { echo "$c 0 0 0 0"; continue; }
  base="/sys/fs/cgroup/system.slice/docker-${id}.scope"
  usage=$(awk '/^usage_usec/{print $2}' "$base/cpu.stat" 2>/dev/null)
  thr=$(awk '/^throttled_usec/{print $2}' "$base/cpu.stat" 2>/dev/null)
  nthr=$(awk '/^nr_throttled/{print $2}' "$base/cpu.stat" 2>/dev/null)
  mem=$(cat "$base/memory.current" 2>/dev/null)
  echo "$c ${usage:-0} ${thr:-0} ${nthr:-0} ${mem:-0}"
done

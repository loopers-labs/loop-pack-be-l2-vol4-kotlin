#!/bin/bash
# Kafka 컨슈머 랙 샘플러 — 5초 간격, 토픽·파티션별 1행 CSV. infra 노드에서 실행 (docker exec 경유).
#   micrometer records_lag_max(sample-actuator)의 교차 검증 + 파티션 편중(핫 상품 키 쏠림) 관측용.
#   랙 판정: LAG 가 램프 내내 단조 증가(발산)하면 소비 천장 초과. 스파이크 후 회복은 통과.
#   사용: ./load-test/sample-kafka-lag.sh <출력파일> <지속초>
#     (KAFKA_CONTAINER 기본 kafka, GROUP 기본 commerce-streamer-metrics, BOOTSTRAP 기본 localhost:9092)
OUT="${1:?출력 파일 경로 필요}"
DUR="${2:-300}"
C="${KAFKA_CONTAINER:-kafka}"
GROUP="${GROUP:-commerce-streamer-metrics}"
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
echo "epoch,topic,partition,current_offset,log_end_offset,lag" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  TS=$(date +%s)
  docker exec "$C" /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server "$BOOTSTRAP" --describe --group "$GROUP" 2>/dev/null \
    | awk -v ts="$TS" 'NR>1 && $2 ~ /events/ {print ts","$2","$3","$4","$5","$6}' >> "$OUT"
  sleep 5
done

#!/bin/bash
# Kafka 컨슈머 랙 샘플러 — 5초 간격, 그룹·토픽·파티션별 1행 CSV. infra 노드에서 실행 (docker exec 경유).
#   micrometer records_lag_max(sample-actuator)의 교차 검증 + 파티션 편중(핫 상품 키 쏠림) 관측용.
#   랙 판정: LAG 가 램프 내내 단조 증가(발산)하면 소비 천장 초과. 스파이크 후 회복은 통과.
#   사용: ./load-test/sample-kafka-lag.sh <출력파일> <지속초>
#     (KAFKA_CONTAINER 기본 kafka, LAG_GROUPS 기본 = 구독(METRICS·RANKING)×토픽 3종의 6개 그룹 — bash 예약 변수 GROUPS 는 대입이 무시되므로 사용 금지, BOOTSTRAP 기본 localhost:9092)
OUT="${1:?출력 파일 경로 필요}"
DUR="${2:-300}"
C="${KAFKA_CONTAINER:-kafka}"
LAG_GROUPS="${LAG_GROUPS:-commerce-streamer-metrics-product commerce-streamer-metrics-order commerce-streamer-metrics-user-action commerce-streamer-ranking-product commerce-streamer-ranking-order commerce-streamer-ranking-user-action}"
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
echo "epoch,group,topic,partition,current_offset,log_end_offset,lag" > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  TS=$(date +%s)
  for G in $LAG_GROUPS; do
    docker exec "$C" /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server "$BOOTSTRAP" --describe --group "$G" 2>/dev/null \
      | awk -v ts="$TS" -v g="$G" 'NR>1 && $2 ~ /events/ {print ts","g","$2","$3","$4","$5","$6}' >> "$OUT"
  done
  sleep 5
done

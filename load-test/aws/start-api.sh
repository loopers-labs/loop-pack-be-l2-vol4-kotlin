#!/bin/bash
# R9 app 노드 — commerce-api 기동 (R8 start-app.sh 재작성·자산화, 2026-07-16)
# usage: INFRA=<infra 프라이빗IP> [DDL=create] ./start-api.sh
#   첫 부팅만 DDL=create (스키마 생성). 이후 재기동은 기본 none — create 재기동 시 시드 소멸 주의.
#   -Duser.timezone=Asia/Seoul 필수: 앱은 @PostConstruct 에서 TimeZone.setDefault(KST) — 커넥션 풀 생성보다
#   늦어서 Connector/J 가 초기화 시점 TZ(UTC)를 캐시 → LocalDate 바인딩이 -1일로 밀림 (2026-07-16 TRACE 실측:
#   Hibernate 는 16 바인딩, 와이어는 15). JVM 시작부터 KST 고정으로 해소. mysql 서버 TZ(+09:00)는 SQL 날짜 정렬용.
set -euo pipefail
INFRA="${INFRA:?INFRA=<infra 프라이빗IP> 필요}"
DDL="${DDL:-none}"

# POOL 지정 시 Hikari maximum-pool-size / minimum-idle 을 CLI 로 오버라이드 (EXP-POOL 풀 크기 실험용).
#   설정 키는 datasource.mysql-jpa.main.* (DataSourceConfig @ConfigurationProperties). CLI 인자가 profile yaml 을 이긴다.
#   POOL 미지정이면 jpa.yml 기본값(40/30) 그대로 — 하위호환.
POOL_ARGS=()
if [ -n "${POOL:-}" ]; then
  POOL_ARGS=(--datasource.mysql-jpa.main.maximum-pool-size="$POOL" --datasource.mysql-jpa.main.minimum-idle="$POOL")
fi

pkill -f commerce-api.jar || true
sleep 2

MYSQL_HOST="$INFRA" MYSQL_PORT=3306 MYSQL_USER=application MYSQL_PWD=application \
REDIS_MASTER_HOST="$INFRA" REDIS_MASTER_PORT=6379 \
REDIS_REPLICA_1_HOST="$INFRA" REDIS_REPLICA_1_PORT=6380 \
BOOTSTRAP_SERVERS="$INFRA:9092" \
nohup java -Duser.timezone=Asia/Seoul -Xms2g -Xmx2g -jar ~/commerce-api.jar \
  --spring.profiles.active=perf \
  --spring.docker.compose.enabled=false \
  --spring.jpa.hibernate.ddl-auto="$DDL" \
  --management.endpoint.health.probes.enabled=true \
  "${POOL_ARGS[@]}" \
  > ~/api.log 2>&1 &
echo "commerce-api 기동 (DDL=$DDL, POOL=${POOL:-기본40}) — curl localhost:8081/actuator/health 로 확인"

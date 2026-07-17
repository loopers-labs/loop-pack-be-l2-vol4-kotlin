#!/bin/bash
# R9 app 노드 — commerce-streamer 기동 (2026-07-16 신규)
# usage: INFRA=<infra 프라이빗IP> [DDL=create] ./start-streamer.sh
#   streamer 에는 perf 프로파일이 없어 local 프로파일 + 명령행 override 로 기동한다
#   (sut-compose.yml 이 api 에 쓴 방식 — local 의 redis.yml/kafka.yml 하드코딩은 CLI 인자가 이긴다).
#   서버 8083 / management 8084 (application.yml 기설정). 힙 1g — api(2g)와 합쳐 노드 8GB 내.
#   -Duser.timezone=Asia/Seoul 필수 — start-api.sh 와 동일 근거 (@PostConstruct setDefault 가 커넥션 풀보다 늦음).
set -euo pipefail
INFRA="${INFRA:?INFRA=<infra 프라이빗IP> 필요}"
DDL="${DDL:-none}"

pkill -f commerce-streamer.jar || true
sleep 2

nohup java -Duser.timezone=Asia/Seoul -Xms1g -Xmx1g -jar ~/commerce-streamer.jar \
  --spring.profiles.active=local \
  --spring.docker.compose.enabled=false \
  --spring.jpa.hibernate.ddl-auto="$DDL" \
  --spring.jpa.show-sql=false \
  --datasource.mysql-jpa.main.jdbc-url="jdbc:mysql://$INFRA:3306/loopers?rewriteBatchedStatements=true" \
  --datasource.mysql-jpa.main.username=application \
  --datasource.mysql-jpa.main.password=application \
  --spring.kafka.bootstrap-servers="$INFRA:9092" \
  --spring.kafka.admin.properties.bootstrap.servers="$INFRA:9092" \
  --datasource.redis.master.host="$INFRA" \
  --datasource.redis.master.port=6379 \
  --datasource.redis.replicas[0].host="$INFRA" \
  --datasource.redis.replicas[0].port=6380 \
  > ~/streamer.log 2>&1 &
echo "commerce-streamer 기동 (DDL=$DDL) — curl localhost:8084/actuator/health 로 확인"

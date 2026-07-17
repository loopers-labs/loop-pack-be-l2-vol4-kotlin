#!/usr/bin/env bash
# R9 측정 EC2 원샷 부팅+세팅 — 정지 상태에서 "바로 측정 시작" 가능한 지점까지 끌어올린다.
#   순서: start-instances → running 대기 → 새 public IP 취득 → SG 에 현재 IP /32 보장
#         → infra compose 기동(시드 보존 재시작) → MySQL healthy + 시드 행수 확인
#         → 앱 기동(기본 POOL 미지정=40) → health UP → 사용할 env export 출력
#   stop/start 로 public IP 는 바뀌고 private IP(infra .3.214 / app .6.181)는 불변.
#   usage: [POOL=40] [DDL=none] ./boot-and-setup.sh
set -euo pipefail

INFRA_ID=i-00ac704c3344b7849;  INFRA_PRIV=172.31.3.214
APP_ID=i-008a40919c9084d4e;    APP_PRIV=172.31.6.181
SG=sg-069e2ddf71d925f48
KEY=/Users/won/.ssh/chingu-dachi-dev.pem
POOL="${POOL:-}"; DDL="${DDL:-none}"
SSH=(ssh -i "$KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=8 -o UserKnownHostsFile=/dev/null)

echo "▶ 1/6  인스턴스 기동 (infra + app)"
aws ec2 start-instances --instance-ids "$INFRA_ID" "$APP_ID" >/dev/null
aws ec2 wait instance-running --instance-ids "$INFRA_ID" "$APP_ID"

echo "▶ 2/6  새 public IP 취득"
INFRA_PUB=$(aws ec2 describe-instances --instance-ids "$INFRA_ID" --query "Reservations[].Instances[].PublicIpAddress" --output text)
APP_PUB=$(aws ec2 describe-instances --instance-ids "$APP_ID" --query "Reservations[].Instances[].PublicIpAddress" --output text)
echo "   infra=$INFRA_PUB  app=$APP_PUB"

echo "▶ 3/6  SG 에 현재 내 IP /32 보장 (22 / 8080-8081)"
MYIP=$(curl -s https://checkip.amazonaws.com | tr -d '[:space:]')
echo "   내 IP=$MYIP"
aws ec2 authorize-security-group-ingress --group-id "$SG" --protocol tcp --port 22 --cidr "$MYIP/32" 2>/dev/null && echo "   +22 추가" || echo "   22 이미 열림"
aws ec2 authorize-security-group-ingress --group-id "$SG" --ip-permissions IpProtocol=tcp,FromPort=8080,ToPort=8081,IpRanges="[{CidrIp=$MYIP/32}]" 2>/dev/null && echo "   +8080-8081 추가" || echo "   8080-8081 이미 열림"

echo "▶ 4/6  infra compose 기동 (시드 보존 재시작)"
"${SSH[@]}" ec2-user@"$INFRA_PUB" "cd ~ && INFRA_PRIVATE_IP=$INFRA_PRIV sudo -E docker compose -f infra-compose.yml up -d" >/dev/null
for i in $(seq 1 24); do
  H=$("${SSH[@]}" ec2-user@"$INFRA_PUB" 'sudo docker inspect --format "{{.State.Health.Status}}" mysql 2>/dev/null' 2>/dev/null || true)
  echo "   mysql health=$H (${i})"; [ "$H" = "healthy" ] && break; sleep 3
done
ROWS=$("${SSH[@]}" ec2-user@"$INFRA_PUB" 'sudo docker exec mysql mysql -uapplication -papplication loopers -N -e "SELECT COUNT(*) FROM product_ranking_daily"' 2>/dev/null || echo "?")
echo "   product_ranking_daily 행수 = $ROWS  (0 이면 시드 유실 → perf-seed-ranking.sql 재시드 필요)"

echo "▶ 5/6  앱 기동 (POOL=${POOL:-기본40}, DDL=$DDL)"
"${SSH[@]}" ec2-user@"$APP_PUB" "POOL=$POOL DDL=$DDL INFRA=$INFRA_PRIV ~/start-api.sh"
UP=""
for i in $(seq 1 40); do
  if curl -s -m4 "http://$APP_PUB:8081/actuator/health" | grep -q '"status":"UP"'; then UP=1; echo "   health UP (${i})"; break; fi
  sleep 3
done
[ -z "$UP" ] && { echo "   !! 앱 health UP 실패 — 로그 확인"; "${SSH[@]}" ec2-user@"$APP_PUB" 'tail -30 ~/api.log'; exit 1; }
MAX=$(curl -s -m4 "http://$APP_PUB:8081/actuator/prometheus" | awk '/^hikaricp_connections_max\{/{print $2; exit}')
echo "   적용된 hikari max = $MAX"

echo "▶ 6/6  준비 완료. 아래 export 로 측정 시작"
cat <<EX

  export APP=$APP_PUB INFRA_PUB=$INFRA_PUB INFRA_PRIV=$INFRA_PRIV KEY=$KEY

  # 랭킹 읽기 스모크
  curl -s "http://$APP_PUB:8080/api/v1/rankings?date=\$(TZ=Asia/Seoul date +%Y%m%d)&size=20&page=1" | head -c 200; echo
  # 풀 실험 재현      : POOLS="40 80 10" ./load-test/aws/run-pool-arms.sh
  # 단발 S-READ ramp  : k6 run -e BASE_URL=http://$APP_PUB:8080 --summary-export=read.json load-test/k6/ranking-read.js
EX

# RUNBOOK — Week 8 주문 대기열 부하 측정 (EXP-04 / EXP-05)

> 목적: M1(대기열 없는 세계)의 천장 실측 → M2(대기열 세계)가 그 이상을 견딤을 증명 → 스케줄러 토큰 공급량(배치×주기) 산정.
> 이 문서는 프로비저닝부터 각 런 실행까지 **재현 가능한 명령**을 기록한다 (Technical Writing 소스).

## 1. 토폴로지

| 노드 | 사양 | 역할 |
|---|---|---|
| loopers-lt-infra | EC2 m6i.large (2vCPU/8GB, gp3 20GB), ap-northeast-2a | MySQL 8.0 · Redis master/replica · Kafka 3.5.1 (docker compose) |
| loopers-lt-app | EC2 c6i.xlarge (4vCPU/8GB, gp3 12GB), 동일 AZ | commerce-api 단독 (java -jar, perf 프로파일). **앱 1대 고정** |
| 로컬 맥 | — | k6 부하 발생 + actuator 샘플러 |

- 같은 AZ·기본 VPC. SG 하나(`loopers-loadtest-sg`): SSH(22)+앱(8080-8081)은 **내 IP /32만**, 노드 간은 self-referencing 룰로 전체 허용.
- ⚠️ **SSH 터널로 부하를 쏘지 말 것** — k6 포화 구간의 동시 소켓 수천 개에 macOS ssh가 `poll: Invalid argument`로 사망, 런 무효화됨(1회 실측). SG에 내 IP 한정으로 직접 개방이 정답.
- 부하가 인터넷을 지나므로 **지연은 체감치**(RTT ~수십 ms 포함), 판정은 처리량·에러율·actuator 서버 지표 중심.

## 2. 프로비저닝 (실행했던 명령)

```bash
# SG
SGID=$(aws ec2 create-security-group --group-name loopers-loadtest-sg \
  --description "loopers week8 load test - temp" --vpc-id <기본VPC> --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id $SGID --protocol tcp --port 22 --cidr <내IP>/32
aws ec2 authorize-security-group-ingress --group-id $SGID --protocol -1 --source-group $SGID
aws ec2 authorize-security-group-ingress --group-id $SGID --protocol tcp --port 8080-8081 --cidr <내IP>/32

# 인스턴스 (AL2023 x86_64 최신 AMI, user-data: infra=docker+compose / app=corretto 21)
aws ec2 run-instances --image-id <AL2023 AMI> --instance-type m6i.large  ... # infra, 20GB gp3
aws ec2 run-instances --image-id <AL2023 AMI> --instance-type c6i.xlarge ... # app, 12GB gp3
```

## 3. 인프라 기동 (infra 노드)

`infra-compose.yml` = `sut-compose.yml`에서 commerce-api 제외 + 2노드 조정:
- kafka `ADVERTISED_LISTENERS=PLAINTEXT://<infra 프라이빗IP>:9092`
- mysql 튜닝(기록): `--innodb-buffer-pool-size=4G --max-connections=300`

```bash
scp infra-compose.yml perf-seed-l5.sql perf-seed-order-arm.sql ec2-user@<infra>:~/
ssh ec2-user@<infra> 'sudo docker compose -f infra-compose.yml up -d'
```

## 4. 앱 배포 (app 노드)

```bash
# 로컬에서 측정 대상 커밋으로 bootJar 빌드 후 업로드
./gradlew :apps:commerce-api:bootJar && scp apps/commerce-api/build/libs/*.jar ec2-user@<app>:~/commerce-api.jar

# start-app.sh — perf 프로파일 + env 주입. 첫 부팅만 DDL=create (스키마 생성), 이후 기본 none
DDL=create ./start-app.sh   # env: MYSQL_HOST/REDIS_*/BOOTSTRAP_SERVERS=<infra 프라이빗IP>, JVM -Xms2g -Xmx2g
curl localhost:8081/actuator/health   # UP 확인
```

## 5. 시드 (infra 노드, 앱 부팅=스키마 생성 후)

```bash
sudo docker exec -i mysql mysql -uapplication -papplication loopers < perf-seed-l5.sql        # 탐색 배경 (~1분30초)
sudo docker exec -i mysql mysql -uapplication -papplication loopers < perf-seed-order-arm.sql # 주문 타깃 (id 900001~)
```
검증 출력: brands 500 / users 300,000 / products 100,750 / likes 6,814,195 / mismatch 0.
⚠️ 앱을 `DDL=create`로 재기동하면 시드 소멸 — 재기동은 반드시 `DDL=none`(기본값).

## 6. 런 실행 (맥에서 · 배경 탐색 100명/s 공통)

```bash
B=http://<app공인IP>:8080
# 웜업 (JIT·캐시)
k6 run -e MODE=warmup -e ARM=hot -e BASE_URL=$B load-test/k6/order-ramp.js &
k6 run -e MODE=warmup -e PEAK=200 -e BASE=$B load-test/k6/product-list-load.js

# 본 런 공통 패턴: 샘플러 + 배경 탐색 + 주문 램프
ACT=http://<app>:8081/actuator/prometheus ./load-test/sample-actuator.sh <결과.csv> 290 &
k6 run -e RATE=100 -e DURATION=4m10s -e BASE_URL=$B --summary-export=<browse.json> load-test/k6/browse-journey.js &
k6 run -e ARM=hot|spread -e BASE_URL=$B --summary-export=<order.json> load-test/k6/order-ramp.js
```

| 런 | 스크립트 조합 | 시간 |
|---|---|---|
| M1-hot / M1-spread | order-ramp(100→800/s) + browse-journey(100/s) | 각 4분 |
| M1-hot 배경 0 | order-ramp 단독 | 4분 |
| M2-램프 / 스파이크 / 이탈 | queue-order-flow(예정) + browse-journey | 4~6분 |
| M3 방출 스윕·주기 비교 | 앱 재기동 인자: `--queue.scheduler.batch-size=N --queue.scheduler.fixed-delay=MS` | ~15분 |

- 런 사이 쿨다운 ≥20초. 매 런 직후 `run-meta.md`(커밋·기동 인자·부하 조건·시각) 기록.
- 결과: `load-test/results/exp04-aws/`(M1) · `exp05-aws/`(M2·M3) — k6 summary JSON + actuator CSV.

## 7. 정리 (측정 종료 후)

```bash
aws ec2 terminate-instances --instance-ids <infra> <app>
aws ec2 delete-security-group --group-id $SGID   # 인스턴스 종료 후
```

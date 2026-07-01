# Docker Compose 실행 가이드

## 두 개의 스택

| 파일 | 프로젝트명(`name:`) | 포함 서비스 | 용도 |
|---|---|---|---|
| `docker/infra-compose.yml` | `loopers-infra` | MySQL, Redis(master/readonly), Kafka, Kafka-UI | **필수** — 앱 구동에 필요한 인프라 |
| `docker/monitoring-compose.yml` | `loopers-monitoring` | Prometheus, Grafana | **선택** — 지표 모니터링 |

> 모니터링은 안 띄워도 앱은 정상 동작한다. 보통 개발할 때는 인프라만 띄우면 된다.

## 원래(권장) 실행 방식 — 따로 띄우기

```bash
# 1) 인프라 (필수)
docker compose -f docker/infra-compose.yml up -d

# 2) 모니터링 (필요할 때만)
docker compose -f docker/monitoring-compose.yml up -d
```

각 파일에 `name:`이 지정돼 있어 **두 명령을 따로 실행해도 서로 충돌하지 않는다.**

## 한 번에 둘 다 띄우기

```bash
docker compose -p loopers \
  -f docker/infra-compose.yml \
  -f docker/monitoring-compose.yml up -d
```

9개 컨테이너가 `loopers` 단일 프로젝트로 묶여 한 번에 뜬다.

> ⚠️ "따로 띄우기"와 "한 번에 띄우기"를 **섞으면** 프로젝트명이 달라져 컨테이너가 중복 생성된다. 둘 중 한 방식으로 통일할 것.

## 내리기

```bash
docker compose -f docker/infra-compose.yml down
docker compose -f docker/monitoring-compose.yml down

# 한 번에 띄웠다면
docker compose -p loopers \
  -f docker/infra-compose.yml \
  -f docker/monitoring-compose.yml down
```

## 트러블슈팅 — "몇 개만 안 뜬다 / 실패한다"

### 1. 고아 컨테이너(orphan) 경고 + 일부 컨테이너 사라짐 (과거 원인)

**증상**: 한쪽 compose를 띄우면 `Found orphan containers ([...])` 경고가 뜨고,
`--remove-orphans`나 `down` 시 다른 쪽 컨테이너까지 삭제됨.

**원인**: Compose는 `name:`/`-p`가 없으면 **파일이 위치한 디렉터리 이름**으로 프로젝트명을 정한다.
두 파일이 모두 `docker/` 안에 있어 둘 다 프로젝트명이 `docker`로 잡혀, 서로를 같은 프로젝트의 고아로 인식했다.

**해결**: 각 compose 파일 최상단에 `name:`을 지정함 (`loopers-infra`, `loopers-monitoring`). → 이미 적용됨.

### 2. 포트 충돌 (`port is already allocated`)

로컬에 MySQL(3306) · Redis(6379/6380) · Kafka(9092/19092) · Prometheus(9090) · Grafana(3000) · Kafka-UI(9099)가
이미 떠 있으면 **그 서비스만** 실패한다.

```bash
lsof -nP -iTCP -sTCP:LISTEN | grep -E ':(3306|6379|6380|9090|3000|9092)\b'
```

→ 점유 중인 로컬 프로세스를 끄거나 compose의 호스트 포트를 변경.

### 3. Grafana가 늦게 뜨거나 실패

Grafana는 기동 시 인터넷에서 플러그인을 내려받는다. 망이 느리거나 끊기면 Grafana만 지연/실패할 수 있다.

### 4. 죽은 컨테이너 잔재

이전에 충돌로 죽은 컨테이너가 남아 있으면 정리 후 재기동:

```bash
docker compose -f docker/infra-compose.yml down
docker compose -f docker/monitoring-compose.yml down
docker ps -a   # 잔재 확인
```

## 상태 확인

```bash
docker compose -f docker/infra-compose.yml ps
docker ps --format "table {{.Names}}\t{{.Label \"com.docker.compose.project\"}}\t{{.Status}}"
```

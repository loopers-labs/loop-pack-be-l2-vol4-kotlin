# Codex 작업 지침

이 저장소에서 작업할 때는 세부 규칙을 이 문서에 중복해서 적지 않는다.
요청 유형에 맞는 문서를 먼저 확인하고, 해당 문서의 내용을 기준으로 작업한다.

## Codex 진입점

- Codex 는 이 `AGENTS.md` 를 저장소 진입점으로 읽는다.
- 이 문서는 중복 규칙을 담지 않고 `CLAUDE.md`, `agent_rules/`, `docs/design/` 로 라우팅한다.
- `CLAUDE.md` 는 기본 행동 지침과 문서 목록의 원천으로 유지한다.
- `docs/design/` 은 도메인 사실의 SoT 이며, 구현·테스트·아키텍처 판단 전 관련 파일을 함께 확인한다.
- `docs/quest-per-week/` 는 개발 주차별 브랜치 달성목표와 체크리스트가 있는 곳이며, 현재 브랜치 작업상태 평가 전 함께 확인한다.
- `CLAUDE.md`, `agent_rules/`, `docs/design/`, `docs/quest-per-week/` 와 코드가 충돌하면 임의로 선택하지 말고 사용자 확인을 받는다.

## 멀티에이전트 설정

- Codex sub-agent 정의는 `.codex/agents/*.toml` 을 사용한다.
- Claude sub-agent 정의는 `.claude/agents/*.md` 를 원천으로 유지하고, Codex 정의는 생성물로 취급한다.
- 사용자가 멀티에이전트/병렬 작업/위임을 요청하면 작업 성격에 맞는 Codex sub-agent 를 사용한다.
- sub-agent 에게 작업을 위임할 때는 아래 문서 확인 기준에서 해당하는 `CLAUDE.md`, `agent_rules/*`, `docs/design/*` 를 명시적으로 입력 조건에 포함한다.

## 자동 동기화

- `.claude/agents/*.md` 변경 후에는 `python3 .codex/sync-from-claude.py` 로 `.codex/agents/*.toml` 을 갱신한다.
- `.codex/sync-from-claude.py` 는 Codex agent memory 경로를 현재 저장소의 `.codex/agent-memory/<agent>/` 로 보정한다.
- 이 저장소는 `.githooks/` 를 hooksPath 로 사용한다. checkout/merge 후에는 자동 갱신하고, commit 전에는 `.codex/agents` 를 재생성해 drift 를 막는다.
- hooksPath 가 설정되지 않았으면 `git config core.hooksPath .githooks` 를 한 번 실행한다.

## 문서 확인 기준

| 상황 | 먼저 확인할 문서 |
| --- | --- |
| 기본 작업 태도, 수정 범위, 협업 원칙 | `CLAUDE.md` |
| 프로젝트 개요, 기술 스택, 모듈 구조, 실행/검증 명령 | `agent_rules/projectInfo.md` |
| API 요구사항, 도메인 요구사항, 인증/사용자 관련 규칙 | `agent_rules/projectInfo.md` |
| DDD 경계, 패키지 구조, 도메인/JPA 분리, 예외 처리 방향 | `agent_rules/groundRules.md` |
| 테스트 전략, TDD 흐름, 테스트 종류, 픽스처, 모킹 정책 | `agent_rules/groundRules.md` |
| 테스트 파일 분류, 테스트 네이밍, Steps 패턴, API 테스트 기준 | `agent_rules/testing-conventions.md` |
| 이전 PR 리뷰 수정, CodeRabbit/Copilot 리뷰 대응, CI 픽스, 커밋/브랜치/worktree 규칙 | `agent_rules/vcs_rule.md` |
| 개발 주차별 브랜치 달성목표 확인, 현재 작업상태 평가 | `docs/quest-per-week/` 의 해당 주차 문서, `agent_rules/vcs_rule.md` |
| 문서와 구현이 충돌하거나 요구사항 갱신이 필요한 경우 | 사용자 확인 후 관련 `agent_rules/` 문서 |

## 작업 순서

1. 요청 유형에 맞는 문서를 확인한다.
2. 관련 코드와 기존 테스트 패턴을 확인한다.
3. 필요한 최소 범위를 수정한다.
4. 가능한 검증 명령을 실행하고 결과를 보고한다.

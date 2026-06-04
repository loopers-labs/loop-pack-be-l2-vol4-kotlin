# Codex 작업 지침

이 저장소에서 작업할 때는 세부 규칙을 이 문서에 중복해서 적지 않는다.
요청 유형에 맞는 문서를 먼저 확인하고, 해당 문서의 내용을 기준으로 작업한다.

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
| 문서와 구현이 충돌하거나 요구사항 갱신이 필요한 경우 | 사용자 확인 후 관련 `agent_rules/` 문서 |

## 작업 순서

1. 요청 유형에 맞는 문서를 확인한다.
2. 관련 코드와 기존 테스트 패턴을 확인한다.
3. 필요한 최소 범위를 수정한다.
4. 가능한 검증 명령을 실행하고 결과를 보고한다.

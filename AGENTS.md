# AGENTS.md

디프만 19기 2팀 서버. AI 에이전트(Claude Code · Codex)와 사람이 함께 따르는 규칙의 **목차**입니다.
세부 규칙은 아래 하위 문서에 있고, 작업 성격에 맞는 문서를 먼저 읽은 뒤 작업합니다.

## 프로젝트 한눈에

- Kotlin 2.4.21 · Spring Boot 4.1.2 · JDK 25 · Gradle(Kotlin DSL)
- Coroutines · Spring Data JPA · Flyway · Spring Modulith 2.1.1 · PostgreSQL 18 (운영 RDS, 로컬 Docker Compose, 테스트 Testcontainers)
- 패키지 루트: `com.depromeet.team2`

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # 로컬 실행, Docker 필요 (http://localhost:8080/actuator/health)
./gradlew test       # 테스트만
./gradlew ktlintFormat   # 스타일 자동 수정
./gradlew ktlintCheck    # 린트만 (build 에도 포함)
```

## 작업 전에 읽을 문서

| 이럴 때 | 문서 |
|---|---|
| 개발 환경 · 기술 스택 · 설정 · 환경 변수 | [docs/development.md](docs/development.md) |
| 패키지 구조 · 레이어 · 도메인 경계 | [docs/architecture.md](docs/architecture.md) |
| 코드 작성 (Kotlin · Spring · JPA 규칙) | [docs/conventions/code.md](docs/conventions/code.md) |
| 브랜치 · 커밋 · PR | [docs/conventions/git.md](docs/conventions/git.md) |
| 도메인 목록 · 바꾸면 영향받는 도메인 | [docs/domains.yaml](docs/domains.yaml) · [docs/domain-impact.md](docs/domain-impact.md) |
| 왜 이렇게 정했는지 · 결정 기록 | [docs/decisions/README.md](docs/decisions/README.md) |

## 꼭 지킬 것

- `main` · `develop` 에 직접 push 하지 않습니다. 이슈 → 브랜치 → PR 순서로 작업합니다.
- 커밋 · PR 제목은 `Type: [도메인] 내용` 형식입니다. ([git.md](docs/conventions/git.md))
- Controller 는 DTO 만, Service 는 엔티티를 반환하지 않습니다. `*Service.kt` 안에 data class 를 두지 않습니다. (ArchUnit 테스트로 검사, [code.md](docs/conventions/code.md#반환-타입--dto))
- 다른 도메인에는 `service` · `dto` 만 씁니다. 다른 도메인 엔티티는 ID 로만 참조하고, 도메인 간 순환 의존을 만들지 않습니다. (Spring Modulith 로 검사, [architecture.md](docs/architecture.md#도메인-경계-spring-modulith))
- 도메인을 추가하거나 도메인 간 의존을 바꾸면 `docs/domains.yaml` 을 먼저 고치고, 다시 만들어진 `docs/domain-impact.md` 를 함께 커밋합니다.
- 응답은 `ApiResponse` 로 감싸고, 예외는 도메인별 `{Domain}ErrorCode` + `object` 싱글톤 예외로 만듭니다. ([code.md](docs/conventions/code.md#응답--예외))
- DB 스키마는 Flyway 마이그레이션으로만 바꿉니다. `ddl-auto` 로 테이블을 만들지 않습니다.
- 비밀값(DB 비밀번호 · 키)은 커밋하지 않고 환경 변수로 받습니다.
- 커밋 · PR 전에 `./gradlew build` 가 통과해야 합니다. (테스트 + ktlint 포함, 스타일 위반은 `./gradlew ktlintFormat` 으로 먼저 고칩니다)
- 새로 합의한 규칙은 해당 하위 문서에, 결정 배경은 `docs/decisions/` 에 남깁니다.

## 설정 파일: 팀 공유 vs 개인

| 구분 | Claude Code | Codex |
|---|---|---|
| 팀 공유 (커밋) | `CLAUDE.md`(이 문서를 import) · `.claude/settings.json` | `AGENTS.md`(이 문서) |
| 개인 (커밋 안 함) | `CLAUDE.local.md` · `.claude/settings.local.json` · `~/.claude/` | `~/.codex/` |

팀 규칙은 이 문서와 `docs/` 에만 씁니다. 도구별 파일에 규칙을 복사하지 않습니다.

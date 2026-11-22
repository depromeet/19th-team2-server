# 의사결정 기록

팀에서 정한 것과 그 이유를 남깁니다. 규칙 자체는 해당 문서(architecture · conventions)에 반영하고, 여기에는 **언제 · 무엇을 · 왜** 정했는지만 짧게 적습니다.

## 기록 방법

- 아래 표에 한 줄 추가합니다. 배경이 길면 `NNNN-제목.md` 파일로 따로 쓰고 링크합니다.
- 상태: `확정` · `논의 중` · `폐기`

## 목록

| 날짜 | 결정 | 이유 | 상태 |
|---|---|---|---|
| 2026-11-18 | Kotlin 2.4.21 + Spring Boot 4.1.2 | null 안전성, 코루틴 | 확정 |
| 2026-11-18 | 단일 모듈 · domain - MVC, DDD 모델링 | 가볍게 시작. 헥사고날은 도메인 모델링과 상충, Port 로 파일 증가 | 확정 |
| 2026-11-18 | 인프라 AWS, CI/CD GitHub Actions + Terraform | | 확정 |
| 2026-11-18 | 협업 규칙 · AI 에이전트 설정은 AGENTS.md 로 관리 | Claude Code · Codex 가 같은 규칙을 읽도록 | 확정 |
| 2026-11-20 | JDK 25 | 최신 LTS, 가상 스레드 pinning 해결(JEP 491), 지원 기간 | 확정 |
| 2026-11-20 | DB 는 RDS(RDB), 마이그레이션 Flyway | 핵심 데이터 정합성, RDS 사용 | 확정 |
| 2026-11-20 | 린터 ktlint | Claude · Codex 가 짠 코드를 같은 스타일 기준으로 맞춤 | 확정 |
| 2026-11-20 | Controller 는 DTO 만 반환, Service 는 엔티티 반환 금지 | 엔티티가 API · 계층 밖으로 새지 않게. ArchUnit 테스트로 강제 | 확정 |
| 2026-11-20 | Service 파일 안 data class 금지 | DTO 위치를 dto 패키지로 통일. ArchUnit 테스트로 강제 | 확정 |
| 2026-11-20 | Spring Modulith 로 도메인 경계 검증 · 의존성 맵 자동 생성 | 맵을 코드에서 뽑아 문서와 코드가 어긋나지 않게. 엔티티는 다른 도메인을 ID 로만 참조 | 확정 |
| 2026-11-20 | 도메인 레지스트리(`domains.yaml`) + 영향 범위 문서 자동 생성 | 기획 단계에서 도메인별 영향을 확인하고, 코드가 레지스트리와 어긋나면 테스트로 잡기 위해 | 확정 |
| 2026-11-20 | 응답 형식 `{isSuccess, code, message, result}` (STORIX-BE-2.0 기반) | 클라이언트가 응답 모양 하나만 알면 되게 | 확정 |
| 2026-11-20 | 성공 코드는 `OK` · `CREATED` 만 | API 별 성공 코드는 수백 개로 늘고 클라이언트가 거의 안 씀 | 확정 |
| 2026-11-20 | 에러 코드는 도메인별 enum, `{도메인}_{사유}` (번호 없음) | 한 enum 을 모든 도메인이 고치면 충돌 · 경계 흐림. 번호는 의미가 없음 | 확정 |
| 2026-11-20 | 비즈니스 예외는 `object` 싱글톤 (스택 트레이스 끔) | 재사용 · 비용 없음. 상세 사유가 필요하면 DynamicException | 확정 |
| 2026-11-20 | detekt 보류 | 1.23.x 가 Kotlin 2.4 · JDK 25 미지원 (우회 설정 필요). 2.0 정식 후 재검토 | 논의 중 |
| 2026-11-21 | DB 엔진 PostgreSQL | 공동 사용량 실시간 합산(`UPDATE ... RETURNING`), 세션 겹침 방지(범위 타입 · `EXCLUDE`), 자정을 넘는 시간대 계산, 잦은 스키마 변경 시 DDL 롤백 | 확정 |
| 2026-11-21 | Spring Modulith 는 도메인 경계 검증 · 맵 생성까지만 | 운영은 어노테이션(`spring-modulith-api`)만, 검증은 테스트에서. 이벤트 저장소는 필요해지면 검토 | 확정 |
| 2026-11-22 | 개발·데모 AWS 인프라는 Terraform, 앱 CI/CD는 GitHub Actions + OIDC + SSM Run Command | 서울의 EC2·RDS PostgreSQL 18 한 벌로 시작. SSH 없이 배포하고, 인프라 변경은 CI 검증 후 로컬 수동 적용. 계정·도메인 미정 값은 예제로 제공하고 배포 기본 비활성화. [구성](../infrastructure.md) | 확정 |
| 2026-11-22 | JDK 공급자는 Eclipse Temurin | 로컬 Gradle toolchain·GitHub Actions·EC2의 JDK 공급자를 통일 | 확정 |

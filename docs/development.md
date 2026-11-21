# 개발 환경

## 기술 스택

| 항목 | 버전 · 선택 | 비고 |
|---|---|---|
| 언어 | Kotlin 2.4.21 | |
| 프레임워크 | Spring Boot 4.1.2 (Web MVC) | |
| JDK | 25 | Gradle toolchain 이 없으면 자동으로 받음 (foojay) |
| 빌드 | Gradle 9.9.1 · Kotlin DSL | 래퍼(`./gradlew`) 사용, 따로 설치 불필요 |
| 코루틴 | kotlinx-coroutines 1.10.2 (core · reactor) | Spring Boot 가 버전 관리. MVC 컨트롤러 · 서비스에서 `suspend` 사용 가능 |
| ORM | Spring Data JPA | `plugin.jpa` · `allOpen` 적용 |
| 마이그레이션 | Flyway | `src/main/resources/db/migration` |
| DB | PostgreSQL 18 (운영 AWS RDS) | 로컬은 Docker Compose, 테스트는 Testcontainers 로 같은 PostgreSQL 을 씁니다 |
| 도메인 경계 | Spring Modulith 2.1.1 | 운영은 `spring-modulith-api`(어노테이션만), 검증 · 맵 생성은 테스트(`spring-modulith-starter-test`)에서만 돕니다. [architecture.md](architecture.md#도메인-경계-spring-modulith) |
| 린터 | ktlint 1.8.0 | [code.md](conventions/code.md#린터--포매터) |
| 인프라 · CI/CD | AWS · GitHub Actions · Terraform | 세팅 중 |

## 로컬 실행

Docker 가 켜져 있어야 합니다. 처음 한 번 `.env.example` 을 `.env` 로 복사합니다. (`.env` 는 커밋하지 않습니다)

```bash
cp .env.example .env
./gradlew bootRun
curl localhost:8080/actuator/health   # {"status":"UP"}
```

- `bootRun` 이 `compose.yaml` 의 PostgreSQL 컨테이너를 자동으로 띄우고 접속 정보도 자동으로 연결합니다 (`spring-boot-docker-compose`). 따로 DB 를 설치하거나 설정할 필요가 없습니다.
- 로컬 DB 계정은 `.env` 의 `POSTGRES_DB` · `POSTGRES_USER` · `POSTGRES_PASSWORD` 로 정합니다. `compose.yaml` 에는 기본값이 없어서 `.env` 가 없으면 DB 가 뜨지 않습니다.
- DB 클라이언트로 붙을 때: `localhost:5432` (`127.0.0.1` 에만 열려 있음) / 계정은 `.env` 값
- 테스트(`./gradlew test`)는 Testcontainers 가 PostgreSQL 컨테이너를 따로 띄워서 씁니다. 테스트에서 DB 가 필요하면 `@Import(TestcontainersConfiguration::class)` 를 붙입니다.
- 이미지 버전(`postgres:18`)은 `compose.yaml` · `TestcontainersConfiguration` 두 곳에 있습니다. RDS 버전과 맞춰 함께 바꿉니다.

IntelliJ 에서는 Project SDK 를 JDK 25 로 맞춥니다.

## 설정 파일

- `src/main/resources/application.yaml`: 공통 설정
  - `spring.jpa.hibernate.ddl-auto: validate`: 스키마는 Flyway 가 관리
  - `spring.jpa.open-in-view: false`
  - `app.cors.allowed-origins`: 허용할 프론트 origin 목록. 공개되는 값이라 yaml 에 바로 적습니다
- `src/main/resources/application-prod.yaml`: 운영(RDS) 접속 정보를 환경 변수로 받습니다. `SPRING_PROFILES_ACTIVE=prod` 로 실행합니다.

## 타임존

서버 시각은 `Asia/Seoul` 로 고정합니다. AWS 기본 타임존이 UTC 여도 `LocalDateTime.now()` · 로그 시각이 한국 시간이 되게 하기 위해서입니다.

- 실행: `Team2ServerApplication.kt` 의 `main()` 에서 Spring 을 띄우기 전에 고정합니다.
- 테스트: 테스트는 `main()` 을 거치지 않아서 `build.gradle.kts` 의 테스트 설정에 `-Duser.timezone=Asia/Seoul` 을 따로 줍니다. 중복처럼 보여도 지우지 않습니다.

## 환경 변수

비밀값은 코드 · yaml 에 넣지 않고 환경 변수로 받습니다. 로컬 `.env` 는 커밋하지 않습니다(`.gitignore`).

| 변수 | 용도 | 상태 |
|---|---|---|
| `DB_URL` | RDS 접속 URL (예: `jdbc:postgresql://{RDS 엔드포인트}:5432/team2`) | `prod` 필수 |
| `DB_USERNAME` | RDS 계정 | `prod` 필수 |
| `DB_PASSWORD` | RDS 비밀번호 | `prod` 필수 |
| `SPRING_PROFILES_ACTIVE` | 운영은 `prod` | 운영 필수 |

## 알아둘 것: ktlint 와 Kotlin 버전

`build.gradle.kts` 에 ktlint 용 우회 설정이 있습니다. 지우면 ktlint 가 깨집니다.

- Spring 의존성 관리 플러그인이 모든 configuration 의 Kotlin 을 2.4.21 로 올립니다. ktlint 는 내장 Kotlin 컴파일러(2.2.21)로 돌아야 해서 ktlint configuration 만 되돌립니다.
- ktlint 버전을 올릴 때는 그 버전의 내장 Kotlin 버전으로 이 값도 같이 바꿉니다.

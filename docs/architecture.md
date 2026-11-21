# 아키텍처

> 상태: **큰 틀만 확정** (2026-11-18 서버 회의). 세부 레이어는 논의 중입니다.

## 확정

- 단일 모듈 · **domain - MVC** 구조로 가볍게 시작합니다.
- 도메인 모델링은 **DDD** 로 합니다.
- 헥사고날(Port · Adapter)은 쓰지 않습니다.
  - 의존성 역전이 도메인 모델링과 상충하고, Port 때문에 도메인마다 파일이 늘어나서입니다.
  - 도메인 간 내부 통신은 내부 API(다른 도메인의 Service 호출)로 합니다.

## 패키지 구조 (초안)

```
com.depromeet.team2
├── {domain}/            # 예: member, auth ...
│   ├── controller/      # 요청 · 응답, DTO 변환
│   ├── service/         # 비즈니스 로직, 트랜잭션 경계
│   ├── repository/      # Spring Data JPA
│   ├── entity/          # JPA 엔티티 · 도메인 모델
│   ├── exception/       # {Domain}ErrorCode, 도메인 예외(object)
│   └── dto/
└── global/              # 공통: config(JPA Auditing · CORS), exception(에러 코드 · 예외 · 핸들러), payload(공통 응답 · 성공 코드), entity(BaseTimeEntity)
```

의존 방향: `controller → service → repository`. 다른 도메인의 repository 를 직접 쓰지 않고 그 도메인의 service 를 통합니다.

## 도메인 경계 (Spring Modulith)

`com.depromeet.team2` 바로 아래 패키지 하나가 도메인(모듈) 하나입니다. `ModularityTest` 가 아래 규칙을 검사하고, 어기면 `./gradlew build` 가 실패합니다.

- **다른 도메인에는 `service` · `dto` 패키지만 공개합니다.** `controller` · `repository` · `entity` 는 그 도메인 안에서만 씁니다.
  - 그래서 다른 도메인의 엔티티를 직접 참조할 수 없습니다. 엔티티 간 참조는 **ID 로만** 합니다. (`@ManyToOne val author: Member` ❌ → `val authorId: Long` ✅)
- **도메인 간 순환 의존을 만들지 않습니다.** (member → post → member ❌)
- **`global` 은 공통 모듈**이라 모든 도메인이 하위 패키지까지 쓸 수 있습니다. (`global/GlobalModule.kt`, OPEN 모듈)

### 새 도메인 만들 때

1. [`docs/domains.yaml`](domains.yaml) 에 도메인 · 설명 · 허용 의존(`dependsOn`)을 등록합니다. (기획 단계에서 먼저 등록해도 됩니다)
2. `service` · `dto` 패키지에 공개 선언 파일을 하나씩 둡니다. 없으면 다른 도메인이 그 패키지를 쓸 때 테스트가 실패합니다.
(Kotlin 은 `package-info.java` 를 못 만들어서 `@PackageInfo` 클래스로 대신합니다.)

```kotlin
// {domain}/service/ServiceApi.kt
@PackageInfo
@NamedInterface("service")
class ServiceApi

// {domain}/dto/DtoApi.kt
@PackageInfo
@NamedInterface("dto")
class DtoApi
```

### 도메인 레지스트리 · 영향 범위

- [`docs/domains.yaml`](domains.yaml): 도메인 목록 · 책임 · 허용 의존. `DomainRegistryTest` 가 코드와 비교합니다.
  - 코드에 있는데 등록 안 된 도메인 → 실패
  - `dependsOn` 에 없는 도메인을 코드에서 의존 → 실패 (의도한 의존이면 `dependsOn` 에 추가)
  - 등록만 되고 코드가 없는 도메인은 "기획 중" 으로 허용
- [`docs/domain-impact.md`](domain-impact.md): **자동 생성**. 도메인별 "바꾸면 영향받는 도메인" 표 + 의존 그래프(Mermaid).
  - 기능 기획 시 바꿀 도메인의 영향 범위를 여기서 확인합니다. 기획 중 도메인의 예정 의존도 포함됩니다.
  - 로컬 `./gradlew test` 가 다시 만들고, CI(`CI=true`)에서는 최신이 아니면 실패합니다. 바뀌면 함께 커밋합니다.

### 의존성 맵 · 모듈 캔버스

```bash
./gradlew test --tests '*ModularityTest*'
# → build/spring-modulith-docs/
#    components.puml        전체 도메인 의존성 맵 (PlantUML)
#    module-{도메인}.puml    도메인별 의존 관계
#    module-{도메인}.adoc    모듈 캔버스: Controller · Service · Repository · 엔티티(Aggregate roots) · 다른 도메인 빈 참조
```

코드에서 매번 새로 만들어서 문서가 코드와 어긋나지 않습니다. 맵은 손으로 그리지 않습니다.

## 논의 중

| 주제 | 제안 내용 |
|---|---|
| Modulith 이벤트 저장소 | 도메인 이벤트를 DB 에 기록(Outbox). `spring-modulith-starter-jpa` + Flyway 테이블 필요. 지금 운영 의존성은 `spring-modulith-api` 뿐이라 이벤트를 쓰게 되면 함께 바꿉니다 |

결정되면 위 "확정" 으로 옮기고 [decisions](decisions/README.md) 에 기록합니다.

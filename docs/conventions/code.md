# 코드 컨벤션

> 상태: 기본 규칙만 확정. "논의 중" 항목은 합의 후 확정으로 옮깁니다.

## 린터 · 포매터

| 도구 | 역할 | 설정 파일 |
|---|---|---|
| ktlint 1.8.0 (`org.jlleitschuh.gradle.ktlint`) | 스타일 · 포맷 (Kotlin 공식 컨벤션, `ktlint_official`) | `.editorconfig` |

```bash
./gradlew ktlintFormat         # 스타일 자동 수정
./gradlew ktlintCheck          # 검사만
```

- `./gradlew build` 에 포함돼 있어 위반이 있으면 빌드가 실패합니다.
- 들여쓰기는 공백 4칸(yaml · json 은 2칸), 한 줄 최대 120자입니다.
- 규칙을 끄거나 바꿀 때는 `.editorconfig` 에 이유를 주석으로 남깁니다.
- 아래 "반환 타입 · DTO" 규칙은 ArchUnit 테스트로 자동 검사합니다.

## Kotlin · Spring

- 의존성 주입은 생성자 주입만 씁니다. (Kotlin 주 생성자, `@Autowired` 필드 주입 금지)
- Lombok 은 쓰지 않습니다. Kotlin 클래스 · `data class` 로 대신합니다.
- nullable 타입(`?`)은 필요한 곳에만 쓰고, `!!` 는 쓰지 않습니다.
- 코루틴: 외부 API 호출처럼 기다리는 작업을 동시에 처리할 때 `suspend` · `async` 를 씁니다.
  - JPA(JDBC)는 블로킹이라 `suspend` 로 감싸도 빨라지지 않습니다. 필요하면 `withContext(Dispatchers.IO)` 로 감쌉니다.
  - 테스트는 `kotlinx-coroutines-test` 의 `runTest` 를 씁니다.

## 반환 타입 · DTO

`src/test/kotlin/.../architecture/ArchitectureTest.kt` 가 검사하고, 어기면 `./gradlew build` 가 실패합니다.

- **DTO 는 `dto` 패키지에 둡니다.** (`{domain}/dto/`. 공통 응답 래퍼는 `global/payload/`)
- **Controller 는 DTO 만 반환합니다.**
  - 허용: DTO, `ResponseEntity<ApiResponse<DTO>>`, `List<DTO>` 같은 컬렉션, 반환 없음(`Unit`)
  - 금지: 엔티티, `String` · `Long` 같은 원시 값, `ApiResponse<엔티티>` · `PageResponse<엔티티>` 처럼 감싼 엔티티
- **Service 는 엔티티를 반환하지 않습니다.** `List<엔티티>` 같은 감싼 형태도 금지입니다. `Long`(id) · `Boolean` 같은 값은 반환해도 됩니다.
- **`*Service.kt` 파일 안에 data class 를 정의하지 않습니다.** 중첩 클래스 · 같은 파일의 최상위 클래스 모두 해당합니다. `dto` 패키지로 옮깁니다.

## 응답 · 예외

공통 코드는 `global/` 에 있습니다.

- `exception/`: `ErrorCode`(인터페이스) · `CommonErrorCode` · `CodeException` · `DynamicException` · `GlobalExceptionHandler`
- `payload/`: `ApiResponse` · `SuccessCode` · `ErrorResponse` · `FieldErrorResponse` · `PageResponse`

**성공 응답**: Controller 는 `ApiResponse` 로 감싸 반환합니다. HTTP 상태도 함께 맞춰집니다.

```kotlin
fun get(): ResponseEntity<ApiResponse<MemberResponse>> = ApiResponse.ok(memberService.get(id))
fun create(): ResponseEntity<ApiResponse<MemberResponse>> = ApiResponse.created(...)
fun delete(): ResponseEntity<ApiResponse<Unit>> = ApiResponse.ok()
```

```json
{ "isSuccess": true, "code": "OK", "message": "요청에 성공했습니다.", "result": { ... }, "timestamp": "..." }
{ "isSuccess": false, "code": "MEMBER_NOT_FOUND", "message": "...", "detail": "...", "fieldErrors": [ ... ], "timestamp": "..." }
```

- 페이지 조회는 `Page` 를 그대로 내보내지 않고 `PageResponse.from(page)` 로 감쌉니다.
- 성공 코드는 `OK` · `CREATED` 두 개만 씁니다. API 마다 성공 코드를 만들지 않습니다.

**에러 코드**: 도메인마다 `{Domain}ErrorCode` enum 을 만들고 `ErrorCode` 를 구현합니다.

```kotlin
// member/exception/MemberErrorCode.kt
enum class MemberErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
}
```

- 코드 = enum 상수 이름, `{도메인}_{사유}` 형식입니다. 번호(`_001`)를 붙이지 않습니다.
- 공통 에러는 `CommonErrorCode` (`COMMON_*`) 에 있습니다.
- 형식과 전체 enum 간 중복은 `ErrorCodeTest` 가 검사합니다.

**예외**: 도메인 예외는 에러 코드와 같은 `{domain}/exception/` 에 둡니다. 두 종류가 있습니다.

| | `CodeException` | `DynamicException` |
|---|---|---|
| 용도 | 예상된 비즈니스 예외 (없음 · 중복 · 권한) | 호출마다 사유가 다르거나, 원인 예외를 함께 남겨야 할 때 (외부 API 실패 등) |
| 만드는 법 | `object` 싱글톤으로 만들어 재사용 | 던질 때마다 생성 |
| 스택 트레이스 | 수집 안 함 (싱글톤이라 의미가 없고, 비용도 없어짐) | 남김 |
| 응답의 `detail` | 없음 | 4xx 일 때만 실림. 5xx 는 로그에만 남음 |

```kotlin
object MemberNotFoundException : CodeException(MemberErrorCode.MEMBER_NOT_FOUND)
throw MemberNotFoundException

throw DynamicException(MemberErrorCode.MEMBER_NOT_FOUND, detail = "id=$id")
```

- `DynamicException` 의 `cause` 는 이 예외를 일으킨 원래 예외입니다. 다른 예외를 잡아 바꿔 던질 때 넘기면 로그에 `Caused by:` 로 원래 예외와 위치가 남습니다. 원래 예외가 없으면 생략합니다.

```kotlin
try {
    kakaoClient.getUser(token)
} catch (e: HttpClientErrorException) {
    throw DynamicException(AuthErrorCode.AUTH_KAKAO_FAILED, detail = "카카오 응답 ${e.statusCode}", cause = e)
}
```

**전역 예외 처리**: `GlobalExceptionHandler` 가 처리하고, 모든 실패 응답 · 로그는 `respond()` 한 곳에서 만듭니다.

- 처리 대상: 비즈니스 예외, 검증 실패(필드별 에러), 잘못된 JSON · enum 값(필드와 허용값 안내), 404 · 405, 데이터 제약 위반(409), 그 외 예상 못 한 예외(500, 응답에는 공통 메시지만)
- 로그: 5xx 는 `ERROR` + 스택 트레이스(원인 예외 포함), 4xx 는 `WARN` 한 줄 (`code` · `status` · `detail` · `fields`)
- 로그에는 입력값을 남기지 않고 필드명만 남깁니다. (개인정보일 수 있음)

**엔티티 시각**: 엔티티는 `BaseTimeEntity` 를 상속하면 `createdAt` · `updatedAt` 이 자동 기록됩니다.

## JPA

- 엔티티는 `plugin.jpa` · `allOpen` 설정으로 프록시(지연 로딩)가 동작합니다. 설정을 지우지 않습니다.
- 연관관계는 기본 지연 로딩(`FetchType.LAZY`)으로 둡니다.
- 스키마 변경은 Flyway 마이그레이션 파일(`V{번호}__{설명}.sql`)로만 합니다.

## 논의 중

| 주제 | 제안 내용 |
|---|---|
| detekt (코드 품질 린터) | 보류. 1.23.x 가 Kotlin 2.4 · JDK 25 를 지원하지 않아 우회 설정이 필요함. 2.0 정식 출시 후 재검토 |

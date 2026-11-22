# 인프라 구성

서울 리전(`ap-northeast-2`)의 개발·데모 환경을 관리하는 Terraform 코드와 관련 배포 파일의 구조입니다.
인프라는 Terraform, 앱 빌드·배포는 GitHub Actions, DB 스키마는 Flyway가 담당합니다.

AWS 환경 생성과 GitHub 등록은 [최초 세팅 가이드](infrastructure-setup.md)를 참고합니다.

## Terraform 디렉터리

```text
infra/
├── bootstrap/                  # Terraform state 저장소를 관리하는 독립 구성
│   ├── main.tf                 # State용 S3와 암호화·버전 관리·접근 정책
│   ├── backend.tf.example      # bootstrap 자체의 state를 S3에 보관하는 backend 선언 예제
│   └── tests/state.tftest.hcl   # State 버킷 보호·placeholder 검증
├── dev/                        # 개발·데모 서비스 인프라를 관리하는 독립 구성
│   ├── network.tf              # VPC·서브넷·라우팅·보안 그룹
│   ├── compute.tf              # EC2·고정 IP·user data·SSM 배포 문서
│   ├── database.tf             # RDS PostgreSQL·DB 서브넷 그룹·비밀번호 회전 설정
│   ├── storage.tf              # JAR 배포용 S3와 암호화·버전 관리·접근 정책
│   ├── iam.tf                  # EC2 실행 역할·GitHub OIDC provider·배포 역할
│   └── tests/dev.tftest.hcl     # 네트워크·DB·OIDC·HTTPS placeholder 검증
└── templates/
    ├── user-data.sh.tftpl      # EC2 최초 부팅: Temurin·SSM Agent·스크립트·Caddy 설치
    └── team2-server.service    # 앱 프로세스의 systemd 실행·재시작·권한 설정
```

`bootstrap`과 `dev`는 모두 AWS 리소스를 정의합니다. State 저장소를 서비스 인프라보다 먼저 만들고 수명도 별도로 관리하기 위해 분리되어 있습니다.
`bootstrap`은 최초에 로컬 state를 사용하며, S3 backend 선언은 별도 예제로 둡니다. `dev`의 S3 backend는 `versions.tf`에 선언되어 있습니다.

두 구성에는 다음 파일도 공통으로 있습니다.

| 파일                       | 역할                                                                                            |
|----------------------------|-------------------------------------------------------------------------------------------------|
| `versions.tf`              | Terraform·AWS provider 버전 제약, AWS 리전·계정 제한·공통 태그                                  |
| `variables.tf`             | 입력값의 타입·기본값·검증 조건                                                                  |
| `terraform.tfvars.example` | 계정·리소스 사양 등 실제 입력값 파일의 예제                                                     |
| `backend.hcl.example`      | State 버킷·key·리전·암호화·S3 잠금 설정 예제                                                    |
| `outputs.tf`               | bootstrap은 State 버킷·리전, dev는 EC2 ID·고정 IP·RDS endpoint·Secret ARN·GitHub Variables 출력 |
| `.terraform.lock.hcl`      | 선택된 provider 버전과 플랫폼별 패키지 체크섬 고정                                              |

저장소 루트의 `.terraform-version`은 로컬 도구와 CI에서 사용할 Terraform 버전을 지정합니다.

## 현재 AWS 구성

| 대상     | 코드에 정의된 구성                                                                                                                                                        |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC2      | ARM64 Amazon Linux 2023, Eclipse Temurin JDK 25, `t4g.micro`, 암호화된 gp3 8GiB, 고정 IP. JVM heap 상한 `384m`, CPU credit은 Standard 모드. `t4g.small`도 입력값으로 허용 |
| 네트워크 | VPC `10.20.0.0/16`, EC2용 퍼블릭 서브넷 하나, 서로 다른 AZ의 DB용 프라이빗 서브넷 둘. 외부 인바운드는 80·443, DB 5432는 EC2 보안 그룹에서만 허용                          |
| RDS      | PostgreSQL 18.x, Single-AZ, 암호화된 gp3, DB 이름 `team2`. 백업 7일·삭제 보호·최종 스냅샷 사용, 자동 마이너 업그레이드 비활성화. 버전·클래스·용량은 입력값으로 지정       |
| DB 인증  | RDS가 관리 계정 비밀번호를 생성해 Secrets Manager에 저장. 자동 회전 비활성화. 앱 시작마다 조회해 환경 변수로 전달하고 `team2` 사용자로 JVM 실행                           |
| HTTPS·앱 | `enable_https=true`일 때 Caddy가 인증서를 관리하고 `127.0.0.1:8080`으로 프록시. Spring은 `prod` 프로필을 사용하며 프론트 origin은 `SPRING_APPLICATION_JSON`으로 주입      |
| S3       | State용 버킷과 배포 JAR용 버킷 분리. 모두 비공개·암호화·버전 관리·TLS 접근 정책 적용. State 버킷에는 `prevent_destroy` 적용                                               |
| IAM·SSM  | GitHub는 OIDC 임시 권한으로 배포용 S3·SSM에 접근. EC2는 IAM 역할로 JAR·DB 비밀번호 조회. 개발자 접속은 Session Manager, 배포는 Run Command 사용. SSH 키·22번 포트 미사용  |

State는 같은 버킷의 `bootstrap/terraform.tfstate`와 `dev/terraform.tfstate`로 분리하고 S3 `use_lockfile`로 잠급니다.
EC2·GitHub 배포 역할에는 State 접근 권한을 부여하지 않습니다. NAT Gateway·ALB·전용 SSM endpoint·DNS 레코드는 Terraform 관리 대상에 포함하지 않습니다.

## 설정값의 위치

| 설정                                                         | 위치                                                                      |
|--------------------------------------------------------------|---------------------------------------------------------------------------|
| AWS 계정·리전                                                | 각 구성의 `terraform.tfvars`                                              |
| State 버킷 이름                                              | bootstrap의 `terraform.tfvars`, 두 구성의 `backend.hcl`                   |
| EC2·JVM·RDS 사양, JAR 보관 개수, 배포 버킷, 도메인·프론트 origin·HTTPS 여부 | dev의 `terraform.tfvars`                                                  |
| GitHub 저장소·기존 OIDC provider ARN                         | dev의 `terraform.tfvars`. 기본 저장소는 `depromeet/19th-team2-server`     |
| 배포 대상·권한·활성화 여부                                   | dev의 `outputs.tf`에 정의된 `github_variables`와 GitHub Actions Variables |

미정 값은 `.example` 파일의 `REPLACE_ME`·`example.com`으로 표시합니다. 기본값은 HTTPS와 개발 배포가 비활성화된 상태입니다.
계정·버킷·DB 사양의 placeholder와 HTTPS 활성화 시의 예제 도메인은 검증 조건으로 차단합니다. RDS 18.x의 실제 지원 사양은 적용 리전에서 확인해야 합니다.

실제 입력 파일(`terraform.tfvars`·`backend.hcl`·bootstrap의 `backend.tf`), state·plan·`.terraform/`는 Git에서 제외합니다.
예제 파일과 두 구성의 `.terraform.lock.hcl`은 공유합니다. DB 비밀번호·AWS Access Key·SSH 키는 Terraform 입력값이나 GitHub Secrets로 관리하지 않습니다.

## 배포·검증 관련 파일

| 위치                                | 역할                                                                                                                                     |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `.github/workflows/ci-cd.yml`       | `app-ci`: Temurin 25 앱 빌드·테스트·JAR 생성. `terraform-ci`: 정적 검증·mock 테스트·배포 스크립트 검사. `deploy-dev`: OIDC 인증·SSM 배포 |
| `scripts/deploy/validate_config.py` | OIDC 인증 전 GitHub Variables의 누락·placeholder 검사                                                                                    |
| `scripts/deploy/dispatch.py`        | JAR 체크섬·AWS 계정 검사, 최신 develop 확인, S3 업로드·SSM 요청·health 확인                                                              |
| `scripts/deploy/server-deploy.sh`   | EC2의 JAR 다운로드·검증·교체·재시작·성공 이력 관리·명시적 복구. 파일 잠금으로 중복 실행 방지                                             |
| `scripts/deploy/server-start.py`    | 앱 시작 시 Secrets Manager 조회, DB·CORS 설정 주입, 사용자 권한을 내린 뒤 JVM 실행                                                       |
| `infra/*/tests`                     | 실제 AWS 리소스 생성 없이 Terraform의 네트워크·DB·권한·보호 정책 검사                                                                    |
| `tests/deploy`                      | 배포 설정·체크섬·실패·복구·중복 실행 검사                                                                                                |

앱 배포 경로는 GitHub Actions → 배포용 S3 → SSM Run Command → EC2의 `server-deploy.sh` → systemd입니다.
`develop`·`main` 대상 PR과 두 브랜치 push는 CI를 실행하고, `develop` push·수동 실행만 CI 통과 및 `DEV_DEPLOY_ENABLED=true` 조건으로 개발 배포를 실행합니다.
Terraform의 실제 변경은 담당자가 로컬에서 검토·적용합니다.

EC2의 JAR 릴리스는 `release_retention_count`로 보관 개수를 정하며 기본값은 5개입니다.
`infra/dev/terraform.tfvars`에서 3 이상의 정수로 바꿀 수 있습니다.
배포 성공·실패 또는 복구 후 실행본(`current`)과 복구 대상(`previous`·`last-successful`)을 우선 보존하고,
남은 자리에는 다운로드 시각이 최신인 릴리스를 남깁니다. 보관 개수를 넘긴 릴리스와 비어 있는 커밋 디렉터리는 삭제합니다.

## 로컬에서 인프라 변경 반영

최초 설정이 완료된 환경에서 저장소 루트 기준으로 실행합니다. 인증 명령은 IAM Identity Center의 `team2-dev` 프로필 기준입니다.

```bash
export AWS_PROFILE=team2-dev
aws sso login --profile "$AWS_PROFILE"
aws sts get-caller-identity

terraform fmt -recursive infra
terraform -chdir=infra/dev init -backend-config=backend.hcl -lockfile=readonly
terraform -chdir=infra/dev validate
terraform -chdir=infra/dev plan -out=tfplan
terraform -chdir=infra/dev show tfplan
```

대상 AWS 계정과 plan의 생성·교체·삭제 내역을 확인한 뒤, 검토한 plan을 적용합니다.

```bash
terraform -chdir=infra/dev apply tfplan
terraform -chdir=infra/dev output
rm infra/dev/tfplan
```

State 버킷 설정 변경은 위 명령의 `infra/dev`를 `infra/bootstrap`으로 바꿔 실행합니다.
EC2 교체 등으로 출력값이 달라지면 GitHub Variables의 해당 값을 갱신하고 앱을 재배포합니다.

## 변경 시 연결되는 부분

- `compute.tf`가 템플릿과 EC2 실행 스크립트를 user data에 포함합니다. 이 내용이나 JVM·도메인 설정 변경 시 EC2가 교체되며, GitHub의 EC2 ID도 갱신 대상입니다.
  고정 IP·RDS·S3는 별도 리소스로 유지되지만 EC2 디스크의 JAR 이력·인증서는 이전되지 않습니다.
- DB 비밀번호는 앱 시작 때 읽으므로 비밀번호 변경은 앱 재시작과 연결됩니다. JAR 복구는 DB 스키마를 되돌리지 않습니다.
- Provider 버전 변경은 두 구성의 버전 제약·lock 파일과 macOS ARM64/Linux AMD64 체크섬을 함께 갱신합니다. Actions는 메이저 버전 태그를 사용하고, 태그 변경은 PR로 관리합니다.

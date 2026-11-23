# 인프라 최초 세팅

AWS 개발·데모 환경을 처음 만드는 담당자를 위한 절차입니다. 모든 명령은 저장소 루트에서 실행합니다.
평소 구성 확인과 변경 반영은 [인프라 구성](infrastructure.md)을 참고합니다.
이미 구축된 환경에 합류한다면 1·2단계에서 기존 설정값을 준비한 뒤 [기존 환경 연결](#7-기존-환경-연결)로 이동합니다.

## 1. 도구·AWS 인증 준비

- Terraform: 저장소의 [`.terraform-version`](../.terraform-version)에 지정된 버전
- AWS CLI v2
- IAM Identity Center 사용자와 인프라 관리 역할

인프라 관리 역할에는 VPC·EC2·RDS·S3·IAM·SSM·Secrets Manager 구성 권한과 State S3 key·`.tflock` 접근 권한이 필요합니다.
앱 배포용 GitHub 역할은 이후 Terraform이 별도로 생성합니다.

IAM Identity Center를 사용한다면 다음 순서로 프로필을 설정합니다. 다른 임시 자격 증명 프로필이 이미 있다면 해당 프로필을 사용합니다.

```bash
aws configure sso --profile team2-dev
export AWS_PROFILE=team2-dev
aws sso login --profile "$AWS_PROFILE"
aws sts get-caller-identity
```

설정 과정에서 SSO URL·SSO 리전은 계정의 값을 입력하고, 기본 AWS 리전은 `ap-northeast-2`로 지정합니다.
마지막 명령의 `Account`가 배포할 계정인지 확인합니다. SSO 사전 준비는 [AWS 인증 설정 안내](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html)를 참고합니다.

## 2. 입력 파일 준비

```bash
cp infra/bootstrap/terraform.tfvars.example infra/bootstrap/terraform.tfvars
cp infra/bootstrap/backend.hcl.example infra/bootstrap/backend.hcl
cp infra/dev/terraform.tfvars.example infra/dev/terraform.tfvars
cp infra/dev/backend.hcl.example infra/dev/backend.hcl
```

생성된 파일의 placeholder를 교체합니다. `bootstrap/backend.tf`는 State 버킷을 만든 뒤 3단계에서 생성합니다.

| 값 | 설정 위치 |
|---|---|
| 실제 12자리 AWS 계정 ID | 두 `terraform.tfvars`의 `aws_account_id` |
| 고유한 State 버킷 이름 | bootstrap의 `state_bucket_name`, 두 `backend.hcl`의 `bucket`에 동일하게 |
| 고유한 배포 버킷 이름 | dev의 `artifact_bucket_name` |
| PostgreSQL 18.x 버전·클래스·gp3 용량 | dev의 `db_engine_version`·`db_instance_class`·`db_allocated_storage` |
| GitHub 저장소·기존 OIDC provider | dev의 `github_repository`·`existing_github_oidc_provider_arn`. 기본 저장소는 `depromeet/19th-team2-server` |
| 도메인·HTTPS 여부 | 우선 `enable_https=false`로 시작. 실제 도메인 연결은 6단계 |

서울 리전에서 생성 가능한 PostgreSQL 18 버전과 사양을 조회합니다. 두 번째 명령의 버전은 첫 번째 조회 결과로 교체합니다.

```bash
aws rds describe-db-engine-versions \
  --engine postgres --region ap-northeast-2 \
  --query "DBEngineVersions[?starts_with(EngineVersion, '18.')].EngineVersion" \
  --output table

aws rds describe-orderable-db-instance-options \
  --engine postgres \
  --engine-version REPLACE_ME_POSTGRES_18_MINOR_VERSION \
  --region ap-northeast-2 \
  --query "OrderableDBInstanceOptions[?StorageType=='gp3'].{Class:DBInstanceClass,MinGiB:MinStorageSize}" \
  --output table

aws iam list-open-id-connect-providers
```

RDS 클래스와 최소 저장 용량은 조회 결과에 맞춥니다. GitHub의 `token.actions.githubusercontent.com` provider가 이미 있다면 그 ARN을 입력합니다.
provider가 없으면 Terraform이 생성합니다.

실제 입력 파일은 Git에서 제외됩니다. DB 비밀번호는 RDS가 생성하므로 입력 파일에 넣지 않습니다.

## 3. State 버킷 생성·S3 이전

이 버킷 생성 작업은 팀에서 최초 한 번 수행합니다.

```bash
terraform fmt -recursive infra
terraform -chdir=infra/bootstrap init -lockfile=readonly
terraform -chdir=infra/bootstrap validate
terraform -chdir=infra/bootstrap plan -out=tfplan
terraform -chdir=infra/bootstrap show tfplan
```

대상 AWS 계정과 생성될 버킷·설정을 확인한 뒤 적용합니다. 저장한 plan을 적용하면 추가 승인 입력 없이 바로 반영됩니다.

```bash
terraform -chdir=infra/bootstrap apply tfplan
rm infra/bootstrap/tfplan

cp infra/bootstrap/backend.tf.example infra/bootstrap/backend.tf
terraform -chdir=infra/bootstrap init \
  -migrate-state \
  -backend-config=backend.hcl \
  -lockfile=readonly

terraform -chdir=infra/bootstrap state list
```

이전 확인 질문에는 `yes`를 입력합니다. `-migrate-state`는 기존 로컬 state를 S3로 복사합니다.
이전 완료를 확인할 때까지 로컬 state 파일을 보관합니다. 자세한 동작은 [Terraform backend 이전 안내](https://developer.hashicorp.com/terraform/cli/commands/init#backend-initialization)를 참고합니다.

## 4. 서비스 인프라 생성

```bash
terraform -chdir=infra/dev init \
  -backend-config=backend.hcl \
  -lockfile=readonly
terraform -chdir=infra/dev validate
terraform -chdir=infra/dev plan -out=tfplan
terraform -chdir=infra/dev show tfplan
```

EC2·RDS·네트워크·IAM 등 생성 내역을 검토한 뒤 적용합니다.

```bash
terraform -chdir=infra/dev apply tfplan
rm infra/dev/tfplan

terraform -chdir=infra/dev output -json github_variables
```

EC2의 최초 부팅 설정은 자동으로 진행됩니다. SSM 배포 명령은 초기 설정 완료를 기다립니다.
DB 비밀번호는 RDS가 생성해 Secrets Manager에 저장하며, 앱 시작 스크립트가 조회합니다.

## 5. GitHub Variables·첫 배포

저장소의 **Settings → Secrets and variables → Actions → Variables**에 `github_variables` 출력의 키와 값을 등록합니다.

| Variable | 초기 설정 |
|---|---|
| `AWS_REGION` | 출력값 (`ap-northeast-2`) |
| `DEV_DEPLOY_ROLE_ARN` | 생성된 GitHub 배포 역할 ARN |
| `DEV_EC2_INSTANCE_ID` | 생성된 EC2 ID |
| `DEV_ARTIFACT_BUCKET` | 생성된 배포 버킷 이름 |
| `DEV_SSM_DOCUMENT_NAME` | 출력값 (`team2-dev-deploy`) |
| `DEV_API_URL` | 도메인 연결 전 빈 값 |
| `DEV_DEPLOY_ENABLED` | 처음에는 `false`, 설정 확인 후 `true` |

AWS Access Key·DB 비밀번호·SSH 키를 GitHub Secrets에 등록하는 단계는 없습니다.
`github_variables`의 `DEV_DEPLOY_ENABLED` 출력은 항상 `false`이므로 이후 출력값 갱신 시 기존 활성화 설정을 확인합니다.

워크플로를 포함한 PR을 `develop`으로 머지하면 CI 통과 후 배포됩니다. 이미 머지했다면 최신 develop 실행의 **Re-run all jobs**로 시작할 수 있습니다.
기본 브랜치에도 워크플로가 있으면 **Run workflow → develop**도 가능합니다. 개발 CD는 `develop`에서 동작합니다.

Actions에서 `App CI`·`Terraform and deployment checks`·`Deploy dev via SSM`의 성공을 확인합니다.
배포 성공에는 EC2 내부 health 확인이 포함됩니다. 브랜치 보호의 필수 검사에는 앞의 두 CI 검사를 등록합니다.

## 6. 도메인·HTTPS 연결

도메인이 준비되면 DNS에서 API 도메인의 A 레코드를 다음 고정 IP로 지정하고 잘못된 AAAA 레코드를 정리합니다.

```bash
terraform -chdir=infra/dev output -raw public_ip
```

dev 입력값의 `api_domain`·`frontend_origin`을 실제 값으로 바꾸고 `enable_https=true`로 설정합니다.
[로컬 변경 반영 순서](infrastructure.md#로컬에서-인프라-변경-반영)로 적용한 뒤, 새 EC2 ID와 HTTPS API 주소를 GitHub Variables에 갱신하고 앱을 재배포합니다.
이 변경에서는 EC2가 교체됩니다. `DEV_API_URL`은 `https://실제-API-도메인` 형식이며, Caddy 인증서 발급 후 외부 HTTPS health까지 배포 검증에 포함됩니다.

## 7. 기존 환경 연결

이미 구축된 환경에 합류할 때는 담당자에게 현재 계정·버킷·사양 등 입력값을 받아 1·2단계를 완료합니다.
기존 State 버킷에 연결합니다.

```bash
cp infra/bootstrap/backend.tf.example infra/bootstrap/backend.tf
terraform -chdir=infra/bootstrap init -backend-config=backend.hcl -lockfile=readonly
terraform -chdir=infra/dev init -backend-config=backend.hcl -lockfile=readonly
```

이후의 변경 작업은 [로컬 변경 반영 순서](infrastructure.md#로컬에서-인프라-변경-반영)를 따릅니다.

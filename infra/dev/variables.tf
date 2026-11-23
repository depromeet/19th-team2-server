variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id)) && var.aws_account_id != "000000000000"
    error_message = "실제 12자리 AWS 계정 ID를 입력하세요."
  }
}

variable "artifact_bucket_name" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", var.artifact_bucket_name)) && !strcontains(lower(var.artifact_bucket_name), "replace")
    error_message = "실제 고유한 소문자 배포 버킷 이름을 입력하세요."
  }
}

variable "github_repository" {
  type    = string
  default = "depromeet/19th-team2-server"
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository)) && !strcontains(lower(var.github_repository), "replace_me")
    error_message = "github_repository는 실제 OWNER/REPOSITORY여야 합니다."
  }
}

variable "existing_github_oidc_provider_arn" {
  description = "계정에 GitHub OIDC provider가 이미 있으면 ARN을 입력합니다. 없으면 새로 만듭니다."
  type        = string
  default     = null
  nullable    = true
  validation {
    condition     = var.existing_github_oidc_provider_arn == null ? true : can(regex("^arn:aws:iam::[0-9]{12}:oidc-provider/token.actions.githubusercontent.com$", var.existing_github_oidc_provider_arn))
    error_message = "기존 GitHub OIDC provider의 ARN을 입력하세요."
  }
}

variable "instance_type" {
  type    = string
  default = "t4g.micro"
  validation {
    condition     = contains(["t4g.micro", "t4g.small"], var.instance_type)
    error_message = "검증한 ARM64 사양인 t4g.micro 또는 t4g.small을 선택하세요."
  }
}

variable "jvm_max_heap" {
  type    = string
  default = "384m"
  validation {
    condition = try(
      tonumber(regex("^([1-9][0-9]*)([mg])$", var.jvm_max_heap)[0]) * (endswith(var.jvm_max_heap, "g") ? 1024 : 1) >= 128,
      false
    )
    error_message = "jvm_max_heap은 초기 heap(-Xms128m) 이상이어야 합니다. 예: 128m, 384m, 1g."
  }
}

variable "release_retention_count" {
  description = "EC2에 보관할 JAR 릴리스 개수. 실행본과 복구 대상을 우선 보존합니다."
  type        = number
  default     = 5
  validation {
    condition     = var.release_retention_count >= 3 && floor(var.release_retention_count) == var.release_retention_count
    error_message = "실행본과 복구 대상을 보존할 수 있도록 3 이상의 정수를 입력하세요."
  }
}

variable "db_engine_version" {
  description = "서울에서 지원되는 PostgreSQL 18 마이너 버전. AWS CLI로 확인 후 입력합니다."
  type        = string
  validation {
    condition     = can(regex("^18\\.[0-9]+$", var.db_engine_version))
    error_message = "지원되는 PostgreSQL 18 마이너 버전(18.x)을 입력하세요."
  }
}

variable "db_instance_class" {
  type = string
  validation {
    condition     = can(regex("^db\\.[a-z0-9]+\\.[a-z0-9]+$", var.db_instance_class))
    error_message = "PostgreSQL 18에서 생성 가능한 실제 DB 클래스를 입력하세요."
  }
}

variable "db_allocated_storage" {
  type    = number
  default = 20
  validation {
    condition     = var.db_allocated_storage >= 20 && floor(var.db_allocated_storage) == var.db_allocated_storage
    error_message = "gp3 최소 용량과 AWS 조회 결과에 맞는 정수 GiB를 입력하세요."
  }
}

variable "enable_https" {
  type    = bool
  default = false
}

variable "api_domain" {
  type    = string
  default = "api.example.com"
  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}$", var.api_domain))
    error_message = "api_domain에는 프로토콜 없이 소문자 DNS 이름만 입력하세요."
  }
}

variable "frontend_origin" {
  type    = string
  default = "https://app.example.com"
  validation {
    condition     = can(regex("^https?://[A-Za-z0-9.-]+(:[0-9]+)?$", var.frontend_origin))
    error_message = "경로와 마지막 / 없이 실제 프론트 origin을 입력하세요."
  }
}

variable "caddy_version" {
  type    = string
  default = "2.11.9"
  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.caddy_version))
    error_message = "검증한 Caddy 릴리스 버전을 입력하세요."
  }
}

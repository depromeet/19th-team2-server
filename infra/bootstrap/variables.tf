variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_account_id" {
  description = "실제 AWS 계정 ID. placeholder 상태에서는 plan/apply를 차단합니다."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id)) && var.aws_account_id != "000000000000"
    error_message = "aws_account_id를 실제 12자리 AWS 계정 ID로 바꾸세요."
  }
}

variable "state_bucket_name" {
  description = "AWS 전체에서 고유한 Terraform state 버킷 이름."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", var.state_bucket_name)) && !strcontains(lower(var.state_bucket_name), "replace")
    error_message = "state_bucket_name을 실제 고유한 소문자 버킷 이름으로 바꾸세요."
  }
}

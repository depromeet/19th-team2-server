output "ec2_instance_id" {
  value = aws_instance.app.id
}

output "public_ip" {
  value = aws_eip.app.public_ip
}

output "rds_endpoint" {
  value = aws_db_instance.database.endpoint
}

output "db_secret_arn" {
  value = aws_db_instance.database.master_user_secret[0].secret_arn
}

output "github_variables" {
  description = "GitHub Settings > Secrets and variables > Actions > Variables에 등록. 배포 활성화는 설정 확인 후 수동으로 변경하세요."
  value = {
    AWS_REGION            = var.aws_region
    DEV_DEPLOY_ROLE_ARN   = aws_iam_role.deploy.arn
    DEV_EC2_INSTANCE_ID   = aws_instance.app.id
    DEV_ARTIFACT_BUCKET   = aws_s3_bucket.artifacts.id
    DEV_SSM_DOCUMENT_NAME = aws_ssm_document.deploy.name
    DEV_API_URL           = var.enable_https ? "https://${var.api_domain}" : ""
    DEV_DEPLOY_ENABLED    = "false"
  }
}

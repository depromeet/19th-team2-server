data "aws_ssm_parameter" "ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

locals {
  server_config = jsonencode({
    region                  = var.aws_region
    artifact_bucket         = aws_s3_bucket.artifacts.id
    db_secret_arn           = aws_db_instance.database.master_user_secret[0].secret_arn
    db_url                  = "jdbc:postgresql://${aws_db_instance.database.address}:5432/team2?sslmode=verify-full&sslrootcert=/etc/pki/team2-rds-ca.pem"
    frontend_origin         = var.frontend_origin
    jvm_max_heap            = var.jvm_max_heap
    release_retention_count = var.release_retention_count
  })
}

resource "aws_instance" "app" {
  ami                         = nonsensitive(data.aws_ssm_parameter.ami.value)
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  user_data_replace_on_change = true
  user_data_base64 = base64gzip(templatefile("${path.module}/../templates/user-data.sh.tftpl", {
    server_config = base64encode(local.server_config)
    start_script  = base64encode(file("${path.module}/../../scripts/deploy/server-start.py"))
    deploy_script = base64encode(file("${path.module}/../../scripts/deploy/server-deploy.sh"))
    service_unit  = base64encode(file("${path.module}/../templates/team2-server.service"))
    caddy_version = var.caddy_version
    caddy_config  = base64encode(var.enable_https ? "${var.api_domain} {\n  reverse_proxy 127.0.0.1:8080\n}\n" : "")
    enable_https  = var.enable_https
    aws_region    = var.aws_region
  }))

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  credit_specification {
    cpu_credits = "standard"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 8
    encrypted             = true
    delete_on_termination = true
  }

  tags = { Name = "team2-dev-app" }

  depends_on = [
    aws_route_table_association.public,
    aws_vpc_security_group_egress_rule.app,
    aws_iam_role_policy_attachment.ssm,
    aws_iam_role_policy.app,
    aws_secretsmanager_secret_rotation.database
  ]

  lifecycle {
    precondition {
      condition     = !var.enable_https || (!endswith(var.api_domain, "example.com") && !strcontains(lower(var.frontend_origin), "example.com"))
      error_message = "HTTPS를 켜기 전에 API 도메인과 프론트 origin의 example.com placeholder를 교체하세요."
    }
    precondition {
      condition     = var.existing_github_oidc_provider_arn == null ? true : split(":", var.existing_github_oidc_provider_arn)[4] == var.aws_account_id
      error_message = "기존 OIDC provider와 배포 대상 AWS 계정이 일치해야 합니다."
    }
  }
}

resource "aws_eip" "app" {
  domain     = "vpc"
  instance   = aws_instance.app.id
  depends_on = [aws_internet_gateway.main]
}

resource "aws_ssm_document" "deploy" {
  name            = "team2-dev-deploy"
  document_type   = "Command"
  document_format = "JSON"
  content = jsonencode({
    schemaVersion = "2.2"
    description   = "Deploy a verified team2 JAR, or explicitly roll back after checking DB compatibility"
    parameters = {
      Operation = {
        type              = "String"
        default           = "deploy"
        allowedValues     = ["deploy", "rollback"]
        interpolationType = "ENV_VAR"
      }
      ReleaseKey = {
        type              = "String"
        default           = ""
        allowedPattern    = "^$|^releases/[0-9a-f]{40}/[0-9]+-[0-9]+$"
        interpolationType = "ENV_VAR"
      }
      ArtifactSha256 = {
        type              = "String"
        default           = ""
        allowedPattern    = "^$|^[0-9a-f]{64}$"
        interpolationType = "ENV_VAR"
      }
      ConfirmRollback = {
        type              = "String"
        default           = "no"
        allowedValues     = ["no", "yes"]
        interpolationType = "ENV_VAR"
      }
    }
    mainSteps = [{
      action = "aws:runShellScript"
      name   = "deploy"
      inputs = {
        timeoutSeconds = "600"
        runCommand = [
          "set -eu",
          "cloud-init status --wait",
          "test -f /var/lib/team2/bootstrap-complete",
          "exec /bin/bash /usr/local/sbin/team2-deploy"
        ]
      }
    }]
  })
}

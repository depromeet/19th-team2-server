mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-2a", "ap-northeast-2c"] }
  }
  mock_data "aws_ssm_parameter" {
    defaults = { value = "ami-0123456789abcdef0" }
  }
  mock_resource "aws_db_instance" {
    defaults = {
      master_user_secret = [{
        secret_arn    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:test"
        secret_status = "active"
        kms_key_id    = "arn:aws:kms:ap-northeast-2:123456789012:key/test"
      }]
    }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/test" }
  }
}

variables {
  aws_account_id       = "123456789012"
  artifact_bucket_name = "team2-artifacts-test-123456789012"
  db_engine_version    = "18.1"
  db_instance_class    = "db.t4g.micro"
}

run "private_database_and_no_ssh" {
  command = apply
  assert {
    condition     = !aws_db_instance.database.publicly_accessible && !aws_db_instance.database.multi_az && aws_db_instance.database.storage_encrypted && aws_db_instance.database.deletion_protection
    error_message = "The development database must be private, encrypted and deletion protected."
  }
  assert {
    condition     = toset([for rule in aws_vpc_security_group_ingress_rule.http : rule.from_port]) == toset([80, 443])
    error_message = "Only HTTP and HTTPS can be publicly exposed."
  }
  assert {
    condition     = jsondecode(aws_iam_role.deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:depromeet/19th-team2-server:ref:refs/heads/develop"
    error_message = "PRs and non-develop branches must not obtain the deployment role."
  }
  assert {
    condition     = !var.enable_https && jsondecode(aws_ssm_document.deploy.content).parameters.ReleaseKey.interpolationType == "ENV_VAR"
    error_message = "DNS can remain pending and SSM parameters must not be interpolated as shell source."
  }
  assert {
    condition     = jsondecode(local.server_config).release_retention_count == 5
    error_message = "EC2 deployment configuration must retain five releases by default."
  }
  assert {
    condition     = jsondecode(local.server_config).jvm_max_heap == "384m"
    error_message = "The default maximum heap must remain 384m."
  }
}

run "reject_heap_smaller_than_initial_heap" {
  command = plan
  variables {
    jvm_max_heap = "64m"
  }
  expect_failures = [var.jvm_max_heap]
}

run "reject_heap_just_below_initial_heap" {
  command = plan
  variables {
    jvm_max_heap = "127m"
  }
  expect_failures = [var.jvm_max_heap]
}

run "reject_invalid_heap_unit" {
  command = plan
  variables {
    jvm_max_heap = "128mb"
  }
  expect_failures = [var.jvm_max_heap]
}

run "allow_heap_equal_to_initial_heap" {
  command = plan
  variables {
    jvm_max_heap = "128m"
  }
}

run "allow_heap_in_gibibytes" {
  command = plan
  variables {
    jvm_max_heap = "1g"
  }
}

run "reject_retention_that_cannot_preserve_rollback_targets" {
  command = plan
  variables {
    release_retention_count = 2
  }
  expect_failures = [var.release_retention_count]
}

run "reject_placeholder_https" {
  command = plan
  variables {
    enable_https = true
  }
  expect_failures = [aws_instance.app]
}

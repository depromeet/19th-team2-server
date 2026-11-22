mock_provider "aws" {}

variables {
  aws_account_id    = "123456789012"
  state_bucket_name = "team2-state-test-123456789012"
}

run "private_versioned_state" {
  command = plan
  assert {
    condition     = aws_s3_bucket_public_access_block.state.block_public_policy && aws_s3_bucket_public_access_block.state.restrict_public_buckets
    error_message = "State storage must remain private."
  }
  assert {
    condition     = one(aws_s3_bucket_versioning.state.versioning_configuration).status == "Enabled"
    error_message = "State version recovery must be enabled."
  }
}

run "reject_account_placeholder" {
  command = plan
  variables {
    aws_account_id = "REPLACE_ME_AWS_ACCOUNT_ID"
  }
  expect_failures = [var.aws_account_id]
}

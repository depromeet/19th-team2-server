#!/usr/bin/env python3
"""Validate non-secret GitHub deployment variables before requesting AWS credentials."""

import os
import re
import sys
from urllib.parse import urlsplit


def validate(environment):
    patterns = {
        "AWS_REGION": r"ap-northeast-2",
        "DEV_DEPLOY_ROLE_ARN": r"arn:aws:iam::([0-9]{12}):role/[A-Za-z0-9+=,.@_/-]+",
        "DEV_EC2_INSTANCE_ID": r"i-[0-9a-f]{17}",
        "DEV_ARTIFACT_BUCKET": r"[a-z0-9][a-z0-9-]{1,61}[a-z0-9]",
        "DEV_SSM_DOCUMENT_NAME": r"[A-Za-z0-9_.-]{3,128}",
    }
    for key, pattern in patterns.items():
        value = environment.get(key, "")
        if not re.fullmatch(pattern, value) or "replace" in value.lower():
            raise ValueError(f"Set the real GitHub Actions variable: {key}")
    role_account = environment["DEV_DEPLOY_ROLE_ARN"].split(":")[4]
    if role_account == "000000000000" or set(environment["DEV_EC2_INSTANCE_ID"][2:]) == {"0"}:
        raise ValueError("Replace the placeholder AWS account / EC2 instance ID.")
    url = environment.get("DEV_API_URL", "")
    if url:
        parsed = urlsplit(url)
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.port not in (None, 443)
            or parsed.path not in ("", "/")
            or parsed.query
            or parsed.fragment
            or parsed.hostname == "example.com"
            or parsed.hostname.endswith(".example.com")
            or "replace" in parsed.hostname.lower()
        ):
            raise ValueError("DEV_API_URL must be a real HTTPS origin, or empty before DNS setup.")
    return role_account


if __name__ == "__main__":
    try:
        account = validate(os.environ)
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"aws_account_id={account}\n")
    except ValueError as error:
        sys.exit(str(error))

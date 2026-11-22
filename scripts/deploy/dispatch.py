#!/usr/bin/env python3
"""Upload the CI artifact and wait for one EC2 invocation plus optional HTTPS health."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from validate_config import validate


def aws(*arguments):
    result = subprocess.run(
        ["aws", *arguments, "--region", os.environ["AWS_REGION"], "--output", "json"],
        capture_output=True,
        text=True,
        timeout=60,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "AWS command failed.")
    return json.loads(result.stdout) if result.stdout.strip() else {}


def verify_artifact(directory):
    jar = directory / "app.jar"
    checksum = (directory / "app.jar.sha256").read_text().strip().split()
    if len(checksum) != 2 or checksum[1] != "app.jar" or not re.fullmatch(r"[0-9a-f]{64}", checksum[0]):
        raise ValueError("Invalid artifact checksum file.")
    with jar.open("rb") as source:
        checksum_hash = hashlib.sha256()
        for block in iter(lambda: source.read(1024 * 1024), b""):
            checksum_hash.update(block)
        digest = checksum_hash.hexdigest()
    if digest != checksum[0]:
        raise ValueError("Artifact checksum mismatch.")
    return jar, digest


def is_current_develop(sha):
    repository = os.environ["GITHUB_REPOSITORY"]
    request = Request(
        f"https://api.github.com/repos/{repository}/git/ref/heads/develop",
        headers={
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urlopen(request, timeout=30) as response:
        return json.load(response)["object"]["sha"] == sha


def wait_for_command(command_id, instance_id, timeout=660):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = aws("ssm", "get-command-invocation", "--command-id", command_id, "--instance-id", instance_id)
        except RuntimeError as error:
            if "InvocationDoesNotExist" not in str(error):
                raise
            time.sleep(5)
            continue
        status = result["Status"]
        if status == "Success" and result.get("ResponseCode") == 0:
            return
        if status not in ("Pending", "InProgress", "Delayed"):
            raise RuntimeError(f"SSM invocation failed: {status}. Check command {command_id} on {instance_id}.")
        time.sleep(5)
    raise TimeoutError(f"SSM invocation did not finish within {timeout}s: {command_id}")


def wait_for_https(base_url, attempts=12):
    for attempt in range(attempts):
        try:
            with urlopen(base_url.rstrip("/") + "/actuator/health", timeout=10) as response:
                if response.status == 200 and json.load(response).get("status") == "UP":
                    return
        except (HTTPError, URLError, TimeoutError, ValueError):
            pass
        if attempt + 1 < attempts:
            time.sleep(5)
    raise RuntimeError("HTTPS health check failed after deployment.")


def main():
    account = validate(os.environ)
    if os.environ.get("GITHUB_REF") != "refs/heads/develop":
        raise ValueError("Deployment is allowed only from develop.")
    sha = os.environ["GITHUB_SHA"]
    run = os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[0-9]+-[0-9]+", run):
        raise ValueError("Invalid workflow release identity.")
    jar, digest = verify_artifact(Path("artifact"))
    if aws("sts", "get-caller-identity")["Account"] != account:
        raise ValueError("Authenticated AWS account does not match the deployment role.")
    key = f"releases/{sha}/{run}"
    subprocess.run(
        [
            "aws",
            "s3",
            "cp",
            str(jar),
            f"s3://{os.environ['DEV_ARTIFACT_BUCKET']}/{key}/app.jar",
            "--region",
            os.environ["AWS_REGION"],
            "--only-show-errors",
        ],
        check=True,
        timeout=180,
    )
    if not is_current_develop(sha):
        print("Skipped deployment: this commit is no longer the develop head.")
        return
    instance_id = os.environ["DEV_EC2_INSTANCE_ID"]
    result = aws(
        "ssm",
        "send-command",
        "--instance-ids",
        instance_id,
        "--document-name",
        os.environ["DEV_SSM_DOCUMENT_NAME"],
        "--parameters",
        json.dumps(
            {
                "Operation": ["deploy"],
                "ReleaseKey": [key],
                "ArtifactSha256": [digest],
            }
        ),
        "--timeout-seconds",
        "60",
        "--comment",
        f"GitHub Actions {sha}",
    )
    command_id = result["Command"]["CommandId"]
    print(f"Deploying commit {sha} to {instance_id}; SSM command: {command_id}", flush=True)
    wait_for_command(command_id, instance_id)
    api_url = os.environ.get("DEV_API_URL", "")
    if api_url:
        wait_for_https(api_url)
    else:
        print("DNS is not configured: internal health passed; external HTTPS check is pending.")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"Deployment succeeded\n\n- Commit: `{sha}`\n- EC2: `{instance_id}`\n- SSM: `{command_id}`\n")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, TimeoutError, OSError, subprocess.SubprocessError) as error:
        sys.exit(str(error))

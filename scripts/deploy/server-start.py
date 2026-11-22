#!/usr/bin/python3
"""Retrieve credentials at every start, then replace this root process with an unprivileged JVM."""

import json
import os
import pwd
import subprocess


def main():
    with open("/etc/team2/deploy.json", encoding="utf-8") as source:
        config = json.load(source)
    result = subprocess.run(
        [
            "aws",
            "secretsmanager",
            "get-secret-value",
            "--secret-id",
            config["db_secret_arn"],
            "--region",
            config["region"],
            "--query",
            "SecretString",
            "--output",
            "text",
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=60,
    )
    secret = json.loads(result.stdout)
    environment = {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "LANG": "C.UTF-8",
        "HOME": "/var/lib/team2",
        "SPRING_PROFILES_ACTIVE": "prod",
        "DB_URL": config["db_url"],
        "DB_USERNAME": secret["username"],
        "DB_PASSWORD": secret["password"],
        "SPRING_APPLICATION_JSON": json.dumps(
            {
                "server": {"address": "127.0.0.1", "port": 8080},
                "app": {"cors": {"allowed-origins": [config["frontend_origin"]]}},
            }
        ),
    }
    user = pwd.getpwnam("team2")
    os.setgroups([])
    os.setgid(user.pw_gid)
    os.setuid(user.pw_uid)
    os.execve(
        "/usr/bin/java",
        [
            "/usr/bin/java",
            "-Xms128m",
            f"-Xmx{config['jvm_max_heap']}",
            "-XX:+ExitOnOutOfMemoryError",
            "-jar",
            "/opt/team2/current/app.jar",
        ],
        environment,
    )


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Never put the decrypted secret or AWS CLI stdout/stderr in journald.
        raise SystemExit("Application startup failed; check IAM, DB connectivity and secret configuration.") from None

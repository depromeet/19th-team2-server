import hashlib
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[2] / "scripts" / "deploy"
sys.path.insert(0, str(SCRIPTS))
dispatch = importlib.import_module("dispatch")
validate = dispatch.validate


def configuration():
    return {
        "AWS_REGION": "ap-northeast-2",
        "DEV_DEPLOY_ROLE_ARN": "arn:aws:iam::123456789012:role/team2-dev-github-deploy",
        "DEV_EC2_INSTANCE_ID": "i-1234567890abcdef0",
        "DEV_ARTIFACT_BUCKET": "team2-dev-artifacts-123456789012",
        "DEV_SSM_DOCUMENT_NAME": "team2-dev-deploy",
        "DEV_API_URL": "",
    }


class DispatchTest(unittest.TestCase):
    def test_placeholder_rejected_before_aws_authentication(self):
        for key in ("DEV_DEPLOY_ROLE_ARN", "DEV_EC2_INSTANCE_ID", "DEV_ARTIFACT_BUCKET", "DEV_SSM_DOCUMENT_NAME"):
            values = configuration()
            values[key] = "REPLACE_ME"
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(values)

    def test_dns_can_be_pending_but_placeholder_https_cannot_be_enabled(self):
        self.assertEqual(validate(configuration()), "123456789012")
        for url in ("https://api.example.com", "http://api.my-team.dev", "https://user:pass@api.my-team.dev"):
            values = configuration()
            values["DEV_API_URL"] = url
            with self.subTest(url=url), self.assertRaises(ValueError):
                validate(values)
        values["DEV_API_URL"] = "https://api.my-team.dev"
        validate(values)

    def test_tampered_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "app.jar").write_bytes(b"verified")
            digest = hashlib.sha256(b"verified").hexdigest()
            (root / "app.jar.sha256").write_text(f"{digest}  app.jar\n")
            self.assertEqual(dispatch.verify_artifact(root)[1], digest)
            (root / "app.jar").write_bytes(b"changed")
            with self.assertRaises(ValueError):
                dispatch.verify_artifact(root)

    @patch.object(dispatch.time, "sleep")
    def test_ssm_eventual_consistency_then_success(self, _sleep):
        results = [
            RuntimeError("InvocationDoesNotExist"),
            {"Status": "InProgress"},
            {"Status": "Success", "ResponseCode": 0},
        ]
        with patch.object(dispatch, "aws", side_effect=results) as calls:
            dispatch.wait_for_command("command", "instance")
            self.assertEqual(calls.call_count, 3)

    def test_ssm_terminal_errors_and_nonzero_exit_are_failures(self):
        for result in (
            {"Status": "Failed", "ResponseCode": 1},
            {"Status": "TimedOut"},
            {"Status": "Cancelled"},
            {"Status": "Success", "ResponseCode": 1},
        ):
            with self.subTest(result=result), patch.object(dispatch, "aws", return_value=result):
                with self.assertRaises(RuntimeError):
                    dispatch.wait_for_command("command", "instance")

    def test_ssm_wait_is_bounded(self):
        with self.assertRaises(TimeoutError):
            dispatch.wait_for_command("command", "instance", timeout=0)

    @patch.object(dispatch.time, "sleep")
    def test_https_failure_propagates(self, _sleep):
        with patch.object(dispatch, "urlopen", side_effect=TimeoutError):
            with self.assertRaises(RuntimeError):
                dispatch.wait_for_https("https://api.my-team.dev", attempts=2)

    def test_non_develop_execution_does_not_call_aws(self):
        values = configuration()
        values["GITHUB_REF"] = "refs/heads/main"
        with patch.dict(os.environ, values, clear=True), patch.object(dispatch, "aws") as call:
            with self.assertRaises(ValueError):
                dispatch.main()
            call.assert_not_called()

    def test_stale_commit_does_not_send_deployment_command(self):
        values = configuration()
        values.update(
            {
                "GITHUB_REF": "refs/heads/develop",
                "GITHUB_SHA": "a" * 40,
                "GITHUB_RUN_ID": "100",
                "GITHUB_RUN_ATTEMPT": "1",
            }
        )
        with (
            patch.dict(os.environ, values, clear=True),
            patch.object(dispatch, "verify_artifact", return_value=(Path("app.jar"), "b" * 64)),
            patch.object(dispatch, "aws", return_value={"Account": "123456789012"}) as aws_call,
            patch.object(dispatch.subprocess, "run"),
            patch.object(dispatch, "is_current_develop", return_value=False),
        ):
            dispatch.main()
            self.assertEqual(aws_call.call_count, 1)
            self.assertEqual(aws_call.call_args.args, ("sts", "get-caller-identity"))

    def test_startup_drops_privileges_and_never_writes_secret_file(self):
        spec = importlib.util.spec_from_file_location("server_start", SCRIPTS / "server-start.py")
        launcher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(launcher)
        from io import StringIO
        from types import SimpleNamespace

        config = '{"region":"ap-northeast-2","db_secret_arn":"secret","db_url":"jdbc:test","frontend_origin":"https://front.test","jvm_max_heap":"384m"}'
        secret = SimpleNamespace(stdout='{"username":"user","password":"never-log-me"}')
        order = []
        with (
            patch("builtins.open", return_value=StringIO(config)) as opened,
            patch.object(launcher.subprocess, "run", return_value=secret),
            patch.object(launcher.pwd, "getpwnam", return_value=SimpleNamespace(pw_gid=1000, pw_uid=1000)),
            patch.object(launcher.os, "setgroups", side_effect=lambda _: order.append("groups")),
            patch.object(launcher.os, "setgid", side_effect=lambda _: order.append("gid")),
            patch.object(launcher.os, "setuid", side_effect=lambda _: order.append("uid")),
            patch.object(launcher.os, "execve", side_effect=lambda *args: order.append("exec")) as execute,
        ):
            launcher.main()
            self.assertEqual(order, ["groups", "gid", "uid", "exec"])
            self.assertEqual(opened.call_count, 1)
            self.assertEqual(execute.call_args.args[2]["DB_PASSWORD"], "never-log-me")


if __name__ == "__main__":
    unittest.main()

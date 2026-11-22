import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "deploy" / "server-deploy.sh"


class ServerDeployTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.server = self.root / "server"
        self.binary = self.root / "bin"
        self.binary.mkdir()
        self.source = self.root / "verified.jar"
        self.source.write_bytes(b"verified executable jar")
        self.digest = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.config = self.root / "deploy.json"
        self.config.write_text(json.dumps({"region": "ap-northeast-2", "artifact_bucket": "test-artifacts"}))
        self.log = self.root / "operations.log"
        self.environment = dict(
            os.environ,
            PATH=str(self.binary) + os.pathsep + os.environ["PATH"],
            TEAM2_ROOT=str(self.server),
            TEAM2_CONFIG_FILE=str(self.config),
            TEAM2_HEALTH_ATTEMPTS="2",
            TEAM2_HEALTH_INTERVAL="0",
            TEST_JAR=str(self.source),
            TEST_LOG=str(self.log),
            SSM_Operation="deploy",
            SSM_ReleaseKey="releases/" + "a" * 40 + "/1-1",
            SSM_ArtifactSha256=self.digest,
        )
        self.fake(
            "aws",
            'import os,shutil,sys\nif os.environ.get("FAIL_DOWNLOAD"): sys.exit(1)\nshutil.copyfile(os.environ["TEST_JAR"],sys.argv[4])\n',
        )
        self.fake(
            "systemctl",
            'import os,sys\nwith open(os.environ["TEST_LOG"],"a") as f: f.write(" ".join(sys.argv[1:])+"\\n")\nif sys.argv[1]=="restart" and os.environ.get("FAIL_RESTART"): sys.exit(1)\n',
        )
        self.fake(
            "curl",
            'import os,sys\nprint("{\\"status\\":\\"DOWN\\"}" if os.environ.get("FAIL_HEALTH") else "{\\"status\\":\\"UP\\"}")\n',
        )
        if not shutil.which("flock"):
            self.fake(
                "flock",
                "import fcntl,sys\ntry: fcntl.flock(int(sys.argv[-1]),fcntl.LOCK_EX|fcntl.LOCK_NB)\nexcept BlockingIOError: sys.exit(1)\n",
            )

    def fake(self, name, source):
        script = self.binary / name
        script.write_text("#!" + sys.executable + "\n" + source)
        script.chmod(0o755)

    def execute(self, **environment):
        return subprocess.run(
            ["bash", str(SCRIPT)], env=dict(self.environment, **environment), text=True, capture_output=True, timeout=15
        )

    def assert_success(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_verified_release_starts_and_is_recorded_as_successful(self):
        self.assert_success(self.execute())
        self.assertEqual((self.server / "current" / "app.jar").read_bytes(), self.source.read_bytes())
        self.assertEqual((self.server / "last-successful").resolve(), (self.server / "current").resolve())
        self.assertFalse((self.server / "previous").exists())

    def test_download_or_checksum_failure_does_not_restart_existing_service(self):
        for failure in ({"FAIL_DOWNLOAD": "1"}, {"SSM_ArtifactSha256": "0" * 64}):
            with self.subTest(failure=failure):
                result = self.execute(**failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(self.log.exists())
                self.assertFalse((self.server / "current").exists())
                self.assertEqual(list(self.server.glob(".incoming.*")), [])

    def test_only_latest_five_releases_are_kept(self):
        keys = [f"releases/{8 - index:040x}/{index + 1}-1" for index in range(8)]
        for key in keys:
            self.assert_success(self.execute(SSM_ReleaseKey=key))
        remaining = {str(jar.parent.relative_to(self.server)) for jar in self.server.glob("releases/*/*/app.jar")}
        self.assertEqual(remaining, set(keys[-5:]))
        for key in keys[:-5]:
            self.assertFalse((self.server / key).parent.exists())
        self.assert_success(self.execute(SSM_Operation="rollback", SSM_ConfirmRollback="yes"))
        self.assertEqual((self.server / "current").resolve(), (self.server / keys[-2]).resolve())

    def test_release_retention_count_is_configurable(self):
        configuration = json.loads(self.config.read_text())
        configuration["release_retention_count"] = 3
        self.config.write_text(json.dumps(configuration))
        for run in range(1, 5):
            self.assert_success(self.execute(SSM_ReleaseKey="releases/" + "a" * 40 + f"/{run}-1"))
        self.assertEqual(
            {jar.parent.name for jar in self.server.glob("releases/*/*/app.jar")}, {"2-1", "3-1", "4-1"}
        )

    def test_failed_releases_are_pruned_without_losing_older_rollback_targets(self):
        self.assert_success(self.execute())
        first = (self.server / "current").resolve()
        self.assert_success(self.execute(SSM_ReleaseKey="releases/" + "b" * 40 + "/2-1"))
        last_successful = (self.server / "current").resolve()
        for run in range(3, 10):
            result = self.execute(SSM_ReleaseKey="releases/" + "c" * 40 + f"/{run}-1", FAIL_HEALTH="1")
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue((first / "app.jar").exists())
            self.assertTrue((last_successful / "app.jar").exists())
            self.assertTrue((self.server / "current" / "app.jar").exists())
            self.assertLessEqual(len(list(self.server.glob("releases/*/*/app.jar"))), 5)
        self.assertEqual(
            {jar.parent.name for jar in self.server.glob("releases/" + "c" * 40 + "/*/app.jar")},
            {"7-1", "8-1", "9-1"},
        )
        self.assert_success(self.execute(SSM_Operation="rollback", SSM_ConfirmRollback="yes"))
        self.assertEqual((self.server / "current").resolve(), last_successful)
        self.assert_success(self.execute(SSM_Operation="rollback", SSM_ConfirmRollback="yes"))
        self.assertEqual((self.server / "current").resolve(), first)

    def test_cleanup_does_not_follow_release_directory_symlinks(self):
        outside = self.root / "outside"
        external_release = outside / "1-1"
        external_release.mkdir(parents=True)
        (external_release / "app.jar").write_bytes(b"unrelated")
        releases = self.server / "releases"
        releases.mkdir(parents=True)
        (releases / ("f" * 40)).symlink_to(outside, target_is_directory=True)
        for run in range(1, 7):
            self.assert_success(self.execute(SSM_ReleaseKey="releases/" + "a" * 40 + f"/{run}-1"))
        self.assertEqual((external_release / "app.jar").read_bytes(), b"unrelated")

    def test_failed_releases_do_not_replace_successful_history(self):
        self.assert_success(self.execute())
        successful = (self.server / "last-successful").resolve()
        for run in ("2-1", "3-1"):
            result = self.execute(SSM_ReleaseKey="releases/" + "a" * 40 + "/" + run, FAIL_HEALTH="1")
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual((self.server / "last-successful").resolve(), successful)
            self.assertFalse((self.server / "previous").exists())
        self.assertIn("stop team2-server", self.log.read_text())
        self.assert_success(self.execute(SSM_Operation="rollback", SSM_ConfirmRollback="yes"))
        self.assertEqual((self.server / "current").resolve(), successful)

    def test_restart_failure_is_propagated(self):
        self.assertNotEqual(self.execute(FAIL_RESTART="1").returncode, 0)
        self.assertFalse((self.server / "last-successful").exists())

    def test_manual_rollback_requires_confirmation_and_restores_previous_jar(self):
        self.assert_success(self.execute())
        first = (self.server / "current").resolve()
        self.assert_success(self.execute(SSM_ReleaseKey="releases/" + "b" * 40 + "/2-1"))
        self.assertNotEqual(self.execute(SSM_Operation="rollback").returncode, 0)
        self.assert_success(self.execute(SSM_Operation="rollback", SSM_ConfirmRollback="yes"))
        self.assertEqual((self.server / "current").resolve(), first)

    def test_concurrent_command_is_rejected(self):
        self.server.mkdir()
        with (self.server / "deploy.lock").open("w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.execute()
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Another deployment", result.stderr)
            self.assertFalse(self.log.exists())

    def test_release_key_cannot_escape_release_directory(self):
        self.assertNotEqual(self.execute(SSM_ReleaseKey="releases/../../elsewhere").returncode, 0)
        self.assertFalse(self.log.exists())


if __name__ == "__main__":
    unittest.main()

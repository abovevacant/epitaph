import hashlib
import importlib.util
import io
import json
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "check_proto_schema.py"
spec = importlib.util.spec_from_file_location("check_proto_schema", SCRIPT)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)
PROTO = b'syntax = "proto3";\nmessage Tombstone { uint32 pid = 5; }\n'


def baseline(commit="a" * 40, content=PROTO):
    return {
        "reviewed_commit": commit,
        "schemas": {check.ROOT_SCHEMA: hashlib.sha256(content).hexdigest()},
    }


def config():
    return {
        "repo": "https://example.invalid/core",
        "oldest_release": 16,
        "branches": {"main": baseline(), "android17-release": baseline()},
    }


class GitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.repo = self.directory / "repo"
        self.repo.mkdir()
        self.git("init", "--quiet", "-b", "main")
        self.git("config", "user.name", "Schema test")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.hooksPath", "/dev/null")
        self.proto = self.repo / check.ROOT_SCHEMA
        self.proto.parent.mkdir(parents=True)
        self.proto.write_bytes(PROTO)
        self.commit()
        self.git("branch", "android17-release")
        self.config = config()
        self.config["repo"] = str(self.repo)
        for entry in self.config["branches"].values():
            entry["reviewed_commit"] = self.git("rev-parse", "HEAD").strip()
        self.config_path = self.directory / "upstream.json"

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.repo, check=True, capture_output=True, text=True
        ).stdout

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "--quiet", "-m", "test change")

    def run_check(self):
        self.config_path.write_text(json.dumps(self.config))
        # Exercise the public shell entry point from outside the project root.
        result = subprocess.run(
            [
                str(SCRIPT.with_name("check-proto-schema.sh")),
                "--config",
                str(self.config_path),
            ],
            cwd=self.directory,
            check=False,
            capture_output=True,
            text=True,
        )
        return result.returncode, result.stdout + result.stderr

    def test_unchanged_and_unrelated_commits_pass(self):
        self.assertEqual(0, self.run_check()[0])
        (self.repo / "unrelated.txt").write_text("not a schema")
        self.commit()
        self.assertEqual(0, self.run_check()[0])

    def test_independent_branch_baselines_and_changed_release(self):
        self.git("checkout", "--quiet", "android17-release")
        self.proto.write_bytes(PROTO + b"// new release field\n")
        self.commit()
        status, output = self.run_check()
        self.assertEqual(1, status)
        self.assertIn("Schema changed: android17-release/", output)
        self.assertIn("OK: main/", output)
        self.config["branches"]["android17-release"] = baseline(
            self.git("rev-parse", "HEAD").strip(), self.proto.read_bytes()
        )
        self.assertEqual(0, self.run_check()[0])

    def test_main_changes_are_not_hidden_by_release(self):
        self.proto.write_bytes(PROTO + b"// main changed\n")
        self.commit()
        self.assertEqual(1, self.run_check()[0])

    def test_new_major_qpr_security_and_older_family_qpr_fail_even_if_identical(self):
        for branch in [
            "android18-release",
            "android17-qpr1-release",
            "android18-security-release",
            "android16-qpr3-release",
        ]:
            with self.subTest(branch=branch):
                self.git("branch", branch)
                status, output = self.run_check()
                self.assertEqual(1, status)
                self.assertIn(f"Unreviewed release branch: {branch}", output)
                self.git("branch", "-D", branch)

    def test_old_and_device_specific_branches_are_out_of_scope(self):
        for branch in ["android15-qpr3-release", "android17-d1-release", "development"]:
            self.git("branch", branch)
        self.assertEqual(0, self.run_check()[0])

    def test_missing_branch_fails(self):
        self.git("branch", "-D", "android17-release")
        status, output = self.run_check()
        self.assertEqual(1, status)
        self.assertIn("Missing configured branch: android17-release", output)

    def test_deleted_or_renamed_schema_fails_and_cleans_temporary_repo(self):
        self.proto.rename(self.proto.with_name("renamed.proto"))
        self.commit()
        created = []
        original = tempfile.TemporaryDirectory

        def tracked_temp(**kwargs):
            result = original(dir=self.directory, **kwargs)
            created.append(Path(result.name))
            return result

        with (
            patch.object(
                check.tempfile, "TemporaryDirectory", side_effect=tracked_temp
            ),
            self.assertRaises(check.CheckError),
        ):
            check.collect_git(self.config)
        self.assertTrue(created)
        self.assertTrue(all(not path.exists() for path in created))

    def test_empty_schema_cannot_be_blessed(self):
        self.proto.write_bytes(b"")
        self.commit()
        self.config["branches"]["main"] = baseline(content=b"")
        self.assertEqual(1, self.run_check()[0])

    def test_imports_must_be_tracked_and_checked(self):
        content = PROTO + b'import "extra.proto";\n'
        extra = self.proto.with_name("extra.proto")
        self.proto.write_bytes(content)
        extra.write_bytes(b'syntax = "proto3";\n')
        self.commit()
        self.config["branches"]["main"] = baseline(content=content)
        status, output = self.run_check()
        self.assertEqual(1, status)
        self.assertIn("Untracked import:", output)
        self.config["branches"]["main"]["schemas"]["debuggerd/proto/extra.proto"] = (
            hashlib.sha256(extra.read_bytes()).hexdigest()
        )
        self.assertEqual(0, self.run_check()[0])
        extra.write_bytes(b'syntax = "proto3";\n// changed\n')
        self.commit()
        self.assertEqual(1, self.run_check()[0])

    def test_unreachable_repo_fails(self):
        self.config["repo"] = str(self.directory / "missing")
        self.assertEqual(1, self.run_check()[0])


class CheckerTests(unittest.TestCase):
    def setUp(self):
        self.config = config()

    def test_git_failure_exits_nonzero(self):
        with (
            patch.object(check, "load_config", return_value=self.config),
            patch.object(check, "collect_git", side_effect=check.CheckError("offline")),
            redirect_stderr(io.StringIO()) as output,
        ):
            self.assertEqual(1, check.main([]))
            self.assertIn("ERROR: offline", output.getvalue())

    def test_git_command_errors_are_reported(self):
        for error in [FileNotFoundError("git"), subprocess.TimeoutExpired("git", 120)]:
            with (
                self.subTest(error=error),
                patch.object(check.subprocess, "run", side_effect=error),
                self.assertRaises(check.CheckError),
            ):
                check.git("ls-remote", "--heads", self.config["repo"])

    def test_empty_heads_fail_closed(self):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()) as output:
            self.assertEqual(1, check.report(self.config, {}, {}))
            self.assertIn("Missing configured branch: main", output.getvalue())

    def test_commented_imports_are_ignored(self):
        self.assertEqual(
            [],
            check.untracked_imports(
                b'// import "absent.proto";\n/* import "also-absent.proto"; */\n',
                check.ROOT_SCHEMA,
                {},
            ),
        )

    def test_invalid_config_fails(self):
        for modify in [
            lambda c: c["branches"].pop("main"),
            lambda c: c.update(oldest_release=0),
            lambda c: c["branches"]["main"].update(reviewed_commit="HEAD"),
            lambda c: c["branches"]["main"].update(schemas={}),
        ]:
            bad = deepcopy(self.config)
            modify(bad)
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "upstream.json"
                path.write_text(json.dumps(bad))
                with self.assertRaises(ValueError):
                    check.load_config(path)


if __name__ == "__main__":
    unittest.main()

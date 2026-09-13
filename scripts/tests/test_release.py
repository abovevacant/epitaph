import importlib.util
import io
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "release.py"
spec = importlib.util.spec_from_file_location("epitaph_release", SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("version=0.1.1\n")
        (self.root / "CHANGELOG.md").write_text(
            "# Changelog\n\n## [Unreleased]\n\n### Added\n\n- Feature.\n\n"
            "## [0.1.1] - 2026-03-16\n\n- Previous release.\n\n"
            "[Unreleased]: https://github.com/abovevacant/epitaph/compare/v0.1.1...HEAD\n"
        )
        (self.root / "README.md").write_text(
            "Previous artifact <!-- release:jar-link -->\n"
        )
        self.before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        self.auth_status = 0
        self.calls = []

    def fake_run(self, *args, **kwargs):
        # Model only read-only Git probes and fetching refs. Any commit/tag,
        # staging, upload or push is an unexpected side effect in these tests.
        self.calls.append(args)
        status = 0
        output = ""
        if args[0] == "./gradlew":
            self.assertEqual("checkCentralPortalCredentials", args[-1])
            self.assertIn("--no-configuration-cache", args)
            status = self.auth_status
        elif args == ("git", "rev-parse", "--is-inside-work-tree"):
            output = "true\n"
        elif args == ("git", "symbolic-ref", "--quiet", "--short", "HEAD"):
            output = "main\n"
        elif args[:2] == ("git", "for-each-ref"):
            output = "origin/main\n"
        elif args[:2] == ("git", "status") or args[:2] == ("git", "fetch"):
            pass
        elif args[:4] == ("git", "rev-parse", "-q", "--verify"):
            status = 1
        elif args[:2] == ("git", "ls-remote"):
            status = 2
        else:
            self.fail(f"Unexpected release side effect: {args}")
        return subprocess.CompletedProcess(args, status, output, "")

    def invoke(self, check_mode):
        argv = [str(SCRIPT), *(["--check"] if check_mode else []), "0.2.0"]
        with (
            patch.object(release, "ROOT_DIR", self.root),
            patch.object(release.sys, "argv", argv),
            patch.object(release, "run", side_effect=self.fake_run),
            patch.object(release.subprocess, "run") as upload,
            redirect_stdout(io.StringIO()),
            redirect_stderr(io.StringIO()),
        ):
            try:
                release.main()
            finally:
                upload.assert_not_called()

    def assert_unchanged(self):
        self.assertEqual(
            self.before, {p.name: p.read_bytes() for p in self.root.iterdir()}
        )
        self.assertFalse(any(call[:2] == ("git", "fetch") for call in self.calls))

    def test_check_auth_failure_precedes_all_release_state_changes(self):
        self.auth_status = 1
        with self.assertRaises(SystemExit) as raised:
            self.invoke(check_mode=True)
        self.assertEqual(1, raised.exception.code)
        self.assert_unchanged()

    def test_actual_release_auth_failure_precedes_all_release_state_changes(self):
        self.auth_status = 1
        with self.assertRaises(SystemExit) as raised:
            self.invoke(check_mode=False)
        self.assertEqual(1, raised.exception.code)
        self.assert_unchanged()

    def test_successful_check_does_not_upload_or_write_release_files(self):
        self.invoke(check_mode=True)
        self.assertEqual(
            self.before, {p.name: p.read_bytes() for p in self.root.iterdir()}
        )
        auth_index = next(
            i for i, call in enumerate(self.calls) if call[0] == "./gradlew"
        )
        fetch_index = next(
            i for i, call in enumerate(self.calls) if call[:2] == ("git", "fetch")
        )
        self.assertLess(auth_index, fetch_index)

    def test_release_rechecks_even_after_a_successful_check(self):
        self.invoke(check_mode=True)
        self.calls.clear()
        self.auth_status = 1
        with self.assertRaises(SystemExit):
            self.invoke(check_mode=False)
        self.assertEqual(1, sum(call[0] == "./gradlew" for call in self.calls))
        self.assert_unchanged()

    def test_missing_gradle_cannot_continue(self):
        with (
            patch.object(release, "run", side_effect=OSError("not executable")),
            redirect_stdout(io.StringIO()),
            redirect_stderr(io.StringIO()),
            self.assertRaises(SystemExit),
        ):
            release.check_publishing_credentials()


if __name__ == "__main__":
    unittest.main()

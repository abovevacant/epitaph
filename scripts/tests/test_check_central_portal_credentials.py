import base64
import importlib.util
import io
import json
import unittest
import urllib.error
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import MagicMock, patch

SCRIPT = Path(__file__).resolve().parents[1] / "check_central_portal_credentials.py"
spec = importlib.util.spec_from_file_location("portal_preflight", SCRIPT)
portal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(portal)
USERNAME = "fake-token-user"
PASSWORD = "fake-token-password"
NAMESPACE = "com.abovevacant"
PAYLOAD = {"username": USERNAME, "password": PASSWORD, "namespace": NAMESPACE}


class CredentialTests(unittest.TestCase):
    def setUp(self):
        self.response = MagicMock()
        self.response.status = 200
        self.response.read.return_value = b'{"deployments": []}'
        self.opener = MagicMock()
        self.opener.open.return_value.__enter__.return_value = self.response
        patcher = patch.object(
            portal.urllib.request, "build_opener", return_value=self.opener
        )
        self.build_opener = patcher.start()
        self.addCleanup(patcher.stop)

    def check(self):
        portal.check_credentials(USERNAME, PASSWORD, NAMESPACE)

    def test_read_only_request_matches_publisher_credentials_and_auth_scheme(self):
        self.check()
        request = self.opener.open.call_args.args[0]
        self.assertEqual("GET", request.get_method())
        self.assertEqual(
            "https://central.sonatype.com/api/v1/publisher/deployments"
            "?namespace=com.abovevacant&page=0&size=1",
            request.full_url,
        )
        expected = base64.b64encode(f"{USERNAME}:{PASSWORD}".encode()).decode()
        self.assertEqual(f"UserToken {expected}", request.get_header("Authorization"))
        self.assertEqual(30, self.opener.open.call_args.kwargs["timeout"])
        self.assertIsNone(request.data)
        self.assertIsInstance(self.build_opener.call_args.args[0], portal.NoRedirects)

    def test_missing_credentials_fail_without_network(self):
        for username, password in [("", PASSWORD), (USERNAME, ""), ("   ", PASSWORD)]:
            with (
                self.subTest(username=username),
                self.assertRaises(portal.PreflightError),
            ):
                portal.check_credentials(username, password, NAMESPACE)
        self.build_opener.assert_not_called()

    def test_missing_namespace_fails_without_network(self):
        with self.assertRaises(portal.PreflightError):
            portal.check_credentials(USERNAME, PASSWORD, "")
        self.build_opener.assert_not_called()

    def test_http_errors_fail_closed_without_echoing_credentials(self):
        for status in (301, 302, 400, 401, 403, 404, 429, 500, 503):
            error = urllib.error.HTTPError(
                portal.ENDPOINT, status, PASSWORD, {}, io.BytesIO(PASSWORD.encode())
            )
            self.opener.open.side_effect = error
            with (
                self.subTest(status=status),
                self.assertRaises(portal.PreflightError) as raised,
            ):
                self.check()
            self.assertIn(str(status), str(raised.exception))
            self.assertNotIn(PASSWORD, str(raised.exception))
            self.assertTrue(error.fp.closed)

    def test_network_and_tls_errors_fail_closed(self):
        for error in (
            TimeoutError(PASSWORD),
            urllib.error.URLError(PASSWORD),
            portal.http.client.IncompleteRead(b"partial"),
        ):
            self.opener.open.side_effect = error
            with (
                self.subTest(error=type(error).__name__),
                self.assertRaises(portal.PreflightError) as raised,
            ):
                self.check()
            self.assertNotIn(PASSWORD, str(raised.exception))

    def test_nonstandard_success_status_is_not_accepted(self):
        self.response.status = 204
        with self.assertRaises(portal.PreflightError):
            self.check()

    def test_malformed_or_unexpected_success_bodies_fail_closed(self):
        for body in (
            b"<html>login</html>",
            b"{}",
            b"null",
            b"[]",
            b'{"deployments": {}}',
            b"\xff",
            b"x" * (portal.MAX_RESPONSE_BYTES + 1),
        ):
            self.response.read.return_value = body
            with self.subTest(body=body[:30]), self.assertRaises(portal.PreflightError):
                self.check()

    def test_redirects_never_forward_authorization(self):
        self.assertIsNone(
            portal.NoRedirects().redirect_request(
                None, None, 302, "Found", {}, "https://another-host.invalid"
            )
        )

    def test_success_does_not_print_credentials_or_deployments(self):
        self.response.read.return_value = json.dumps(
            {"deployments": [{"name": PASSWORD}]}
        ).encode()
        with (
            patch.object(portal.sys, "stdin", io.StringIO(json.dumps(PAYLOAD))),
            redirect_stdout(io.StringIO()) as output,
        ):
            self.assertEqual(0, portal.main())
        self.assertIn("no upload performed", output.getvalue())
        self.assertNotIn(PASSWORD, output.getvalue())
        self.assertNotIn(USERNAME, output.getvalue())

    def test_invalid_stdin_does_not_leak_input(self):
        for data in (PASSWORD, "null", "{}", '{"username": 123}'):
            with (
                patch.object(portal.sys, "stdin", io.StringIO(data)),
                redirect_stderr(io.StringIO()) as output,
            ):
                self.assertEqual(1, portal.main())
                self.assertNotIn(PASSWORD, output.getvalue())
        self.build_opener.assert_not_called()


if __name__ == "__main__":
    unittest.main()

"""Non-publishing Central Portal authentication probe, called by Gradle.

Credentials arrive on stdin, never in command-line arguments or a disk file.
The read-only list-deployments endpoint and namespace filter are documented at
https://central.sonatype.com/api-doc (operationId: listDeployments).
"""

import base64
import http.client
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

ENDPOINT = "https://central.sonatype.com/api/v1/publisher/deployments"
MAX_RESPONSE_BYTES = 1024 * 1024


class PreflightError(Exception):
    pass


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Do not forward publishing credentials to any redirected URL.
        return None


def check_credentials(username: str, password: str, namespace: str) -> None:
    if not username.strip() or not password.strip():
        raise PreflightError(
            "Central Portal credentials are missing or empty. Configure "
            "centralPortalUsername/centralPortalPassword in Gradle properties or "
            "CENTRAL_PORTAL_USERNAME/CENTRAL_PORTAL_PASSWORD. Gradle properties "
            "take precedence over those environment variables."
        )
    if not namespace.strip():
        raise PreflightError("The publishing namespace is missing.")

    token = base64.b64encode(f"{username}:{password}".encode()).decode("ascii")
    query = urllib.parse.urlencode({"namespace": namespace, "page": 0, "size": 1})
    request = urllib.request.Request(
        f"{ENDPOINT}?{query}",
        method="GET",
        headers={
            # Match the pinned publishing plugin (1.2.4), including its auth
            # scheme, rather than checking a different authentication path.
            "Authorization": f"UserToken {token}",
            "Accept": "application/json",
        },
    )
    try:
        opener = urllib.request.build_opener(NoRedirects())
        with opener.open(request, timeout=30) as response:
            if response.status != 200:
                raise PreflightError(f"Central Portal returned HTTP {response.status}.")
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        if status in (401, 403):
            raise PreflightError(
                f"Central Portal rejected the publishing credentials (HTTP {status}). "
                "Check or regenerate the Portal user-token username/password pair; "
                "Gradle properties override CENTRAL_PORTAL_* environment variables."
            ) from None
        if status == 404:
            raise PreflightError(
                "Central Portal could not authorize access to the publishing namespace "
                "(HTTP 404). Check the token's namespace access."
            ) from None
        raise PreflightError(
            f"Central Portal authentication check failed (HTTP {status}); "
            "credentials were not verified."
        ) from None
    except (OSError, urllib.error.URLError, http.client.HTTPException):
        # Do not print response bodies, request headers, or exception contents:
        # an intermediary could reflect the credential-bearing request.
        raise PreflightError(
            "Could not reach Central Portal (network, TLS, or timeout error); "
            "credentials were not verified."
        ) from None

    if len(body) > MAX_RESPONSE_BYTES:
        raise PreflightError("Central Portal returned an oversized response.")
    try:
        payload = json.loads(body)
    except (ValueError, UnicodeDecodeError):
        raise PreflightError(
            "Central Portal returned invalid JSON; authentication not verified."
        ) from None
    if not isinstance(payload, dict) or not isinstance(
        payload.get("deployments"), list
    ):
        raise PreflightError(
            "Central Portal returned an unexpected response; authentication not verified."
        )


def main() -> int:
    try:
        payload = json.loads(sys.stdin.read(65536))
        if not isinstance(payload, dict) or any(
            not isinstance(payload.get(key), str)
            for key in ("username", "password", "namespace")
        ):
            raise ValueError
    except ValueError:
        print("Invalid credential payload from Gradle.", file=sys.stderr)
        return 1
    try:
        check_credentials(
            payload["username"], payload["password"], payload["namespace"]
        )
    except PreflightError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(
        "Central Portal credentials and namespace access verified (no upload performed)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

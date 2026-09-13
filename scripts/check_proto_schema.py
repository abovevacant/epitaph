#!/usr/bin/env python3
"""Check reviewed tombstone schemas and discover new Android release branches.

Python 3.10+, standard library only. Network checks are scheduled/manual; tests
use local Git repositories.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT_SCHEMA = "debuggerd/proto/tombstone.proto"
DEFAULT_CONFIG = Path(__file__).resolve().parent.parent / "upstream.json"
RELEASE = re.compile(r"android(\d+)(?:-qpr\d+|-security)?-release\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")


class CheckError(Exception):
    pass


def load_config(path):
    config = json.loads(Path(path).read_text())
    if not isinstance(config.get("repo"), str) or not config["repo"]:
        raise ValueError("repo must be a nonempty URL")

    if type(config.get("oldest_release")) is not int or config["oldest_release"] < 1:
        raise ValueError("oldest_release must be a positive Android version")

    branches = config["branches"]

    if "main" not in branches or len(branches) < 2:
        raise ValueError("configure main and at least one release branch")

    for branch, baseline in branches.items():
        if branch != "main" and not RELEASE.fullmatch(branch):
            raise ValueError(f"unsupported branch name: {branch}")

        if not COMMIT.fullmatch(baseline["reviewed_commit"]):
            raise ValueError(f"invalid reviewed commit for {branch}")

        schemas = baseline["schemas"]

        if ROOT_SCHEMA not in schemas:
            raise ValueError(f"{branch} must track {ROOT_SCHEMA}")

        for schema, digest in schemas.items():
            if (
                schema.startswith("/")
                or ".." in schema.split("/")
                or not schema.endswith(".proto")
            ):
                raise ValueError(f"invalid schema path: {schema}")

            if not SHA256.fullmatch(digest):
                raise ValueError(f"invalid SHA-256 for {branch}/{schema}")

    return config


def branch_problems(config, heads):
    problems = []
    configured = set(config["branches"])
    for branch in sorted(configured - heads.keys()):
        problems.append(f"Missing configured branch: {branch}")

    for branch in sorted(heads.keys() - configured):
        match = RELEASE.fullmatch(branch)

        if match and int(match[1]) >= config["oldest_release"]:
            problems.append(f"Unreviewed release branch: {branch} ({heads[branch]})")

    return problems


def git(*args, cwd=None):
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=cwd,
            capture_output=True,
            check=False,
            timeout=120,
            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise CheckError(f"git {args[0]} failed: {error}") from error

    if result.returncode:
        raise CheckError(
            f"git {args[0]} failed: {result.stderr.decode(errors='replace').strip()}"
        )

    return result.stdout


def collect_git(config):
    repo = config["repo"]
    heads = {}

    for line in git("ls-remote", "--heads", repo).decode().splitlines():
        commit, ref = line.split()

        if not ref.startswith("refs/heads/") or not COMMIT.fullmatch(commit):
            raise CheckError("Invalid Git refs response")

        heads[ref.removeprefix("refs/heads/")] = commit

    schemas = {}

    # A single shallow, blobless fetch of the discovered commits; no full history
    # clone, no branch race between discovery and reading, no persistent cache.
    with tempfile.TemporaryDirectory(prefix="epitaph-schema-") as directory:
        git("init", "--quiet", cwd=directory)
        git("remote", "add", "origin", repo, cwd=directory)
        git("config", "remote.origin.promisor", "true", cwd=directory)
        git("config", "remote.origin.partialclonefilter", "blob:none", cwd=directory)
        commits = sorted({heads[b] for b in config["branches"] if b in heads})

        if commits:
            git(
                "fetch",
                "--quiet",
                "--depth=1",
                "--filter=blob:none",
                "--no-tags",
                "origin",
                *commits,
                cwd=directory,
            )

        for branch, baseline in config["branches"].items():
            if branch in heads:
                for path in baseline["schemas"]:
                    key = (heads[branch], path)
                    if key not in schemas:
                        schemas[key] = git("show", f"{key[0]}:{path}", cwd=directory)

    return heads, schemas


def untracked_imports(content, path, tracked):
    # Preserve strings while stripping comments so commented-out imports don't
    # require tracking. Imports are resolved from the proto directory or repo root.
    text = content.decode("utf-8")
    text = re.sub(
        r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/',
        lambda m: "" if m[0].startswith(("//", "/*")) else m[0],
        text,
        flags=re.DOTALL,
    )
    imports = re.findall(r'\bimport\s+(?:(?:public|weak)\s+)?"([^"]+)"\s*;', text)
    return [
        name
        for name in imports
        if name not in tracked and str(Path(path).parent / name) not in tracked
    ]


def report(config, heads, schemas):
    problems = branch_problems(config, heads)
    for branch, baseline in config["branches"].items():
        if branch not in heads:
            continue

        for path, expected in baseline["schemas"].items():
            content = schemas[(heads[branch], path)]
            actual = hashlib.sha256(content).hexdigest()

            try:
                for name in untracked_imports(content, path, baseline["schemas"]):
                    problems.append(f"Untracked import: {branch}/{path}: {name}")
            except UnicodeDecodeError:
                problems.append(f"Invalid UTF-8 schema: {branch}/{path}")

            if not content or actual != expected:
                problems.append(
                    f"Schema changed: {branch}/{path}\n"
                    f"  Reviewed SHA-256: {expected}\n  Current SHA-256:  {actual}\n"
                    f"  Diff: {config['repo']}/+/{baseline['reviewed_commit']}..{heads[branch]}/{path}"
                )
            else:
                print(f"OK: {branch}/{path} ({heads[branch]})")

    for problem in problems:
        print(problem, file=sys.stderr)

    if problems:
        print(
            "Review upstream and update the model/tests before advancing baselines.",
            file=sys.stderr,
        )
        return 1

    print("All configured schemas are up to date; no unreviewed release branches.")

    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args(argv)

    try:
        config = load_config(args.config)
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        print(f"Invalid configuration: {error}", file=sys.stderr)
        return 1

    try:
        heads, schemas = collect_git(config)
    except CheckError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    return report(config, heads, schemas)


if __name__ == "__main__":
    sys.exit(main())

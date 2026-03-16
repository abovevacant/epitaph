#!/usr/bin/env -S uv run --script
#
# /// script
# requires-python = ">=3.14"
# dependencies = []
# ///

import difflib
import re
import subprocess
import sys
from datetime import date
from pathlib import Path
from typing import NoReturn

ROOT_DIR = Path(__file__).resolve().parent.parent
VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$")


def fail(message: str, exit_code: int = 1) -> NoReturn:
    print(message, file=sys.stderr)
    raise SystemExit(exit_code)


def run(
    *args: str, check: bool = True, capture_output: bool = True
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=ROOT_DIR,
        check=check,
        text=True,
        capture_output=capture_output,
    )


def usage() -> None:
    print("Usage: ./scripts/release.py [--check] <version>", file=sys.stderr)
    print("Example: ./scripts/release.py 0.1.1", file=sys.stderr)
    print("Example: ./scripts/release.py --check 0.1.1", file=sys.stderr)


def parse_args(argv: list[str]) -> tuple[bool, str]:
    if len(argv) == 2:
        return False, argv[1]
    if len(argv) == 3 and argv[1] == "--check":
        return True, argv[2]
    usage()
    raise SystemExit(1)


def read_current_version(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            version = line.partition("=")[2].strip()
            if version:
                return version
    fail("Could not read version from gradle.properties.")


def render_gradle_version(text: str, version: str) -> str:
    updated, count = re.subn(
        r"^version=.*$", f"version={version}", text, count=1, flags=re.MULTILINE
    )
    if count == 0:
        suffix = "" if not text or text.endswith("\n") else "\n"
        updated = f"{text}{suffix}version={version}\n"
    return updated


def render_changelog(text: str, version: str, previous_version: str) -> str:
    if not re.search(r"^## \[Unreleased\]", text, re.MULTILINE):
        fail("CHANGELOG.md must contain an [Unreleased] section before releasing.")

    if re.search(rf"^## \[{re.escape(version)}\]( - .*)?$", text, re.MULTILINE):
        fail(f"CHANGELOG.md already contains a section for {version}.")

    section_re = re.compile(
        r"^## \[Unreleased\]\n(?P<body>.*?)(?=^## \[|\Z)", re.MULTILINE | re.DOTALL
    )
    match = section_re.search(text)
    if match is None:
        fail("Could not find the [Unreleased] section body in CHANGELOG.md")

    body = match.group("body").lstrip("\n").rstrip()
    release_heading = (
        f"## [Unreleased]\n\n## [{version}] - {date.today().isoformat()}\n"
    )
    if body:
        replacement = f"{release_heading}\n{body}\n\n"
    else:
        replacement = f"{release_heading}\n"
    text = f"{text[: match.start()]}{replacement}{text[match.end() :]}"

    unreleased_ref_re = re.compile(r"^\[Unreleased\]: .*$", re.MULTILINE)
    if not unreleased_ref_re.search(text):
        fail("Could not find the [Unreleased] link reference in CHANGELOG.md")
    if re.search(rf"^\[{re.escape(version)}\]: ", text, re.MULTILINE):
        fail(f"CHANGELOG.md already contains a link reference for {version}")

    unreleased_ref = f"[Unreleased]: https://github.com/abovevacant/epitaph/compare/v{version}...HEAD"
    release_ref = (
        f"[{version}]: https://github.com/abovevacant/epitaph/compare/"
        f"v{previous_version}...v{version}"
    )
    text, count = unreleased_ref_re.subn(
        f"{unreleased_ref}\n{release_ref}", text, count=1
    )
    if count != 1:
        fail("Failed to update the [Unreleased] link reference in CHANGELOG.md")

    return text


def relative_label(path: Path) -> str:
    return path.relative_to(ROOT_DIR).as_posix()


def print_diff(path: Path, before: str, after: str) -> None:
    if before == after:
        return
    diff = difflib.unified_diff(
        before.splitlines(keepends=True),
        after.splitlines(keepends=True),
        fromfile=f"a/{relative_label(path)}",
        tofile=f"b/{relative_label(path)}",
    )
    print("".join(diff), end="")


def main() -> None:
    check_mode, version = parse_args(sys.argv)
    tag = f"v{version}"

    if not VERSION_RE.fullmatch(version):
        fail(f"Invalid version: {version}")

    try:
        run("git", "rev-parse", "--is-inside-work-tree")
    except subprocess.CalledProcessError:
        fail("Not inside a git repository.")

    changelog_path = ROOT_DIR / "CHANGELOG.md"
    if not changelog_path.exists():
        fail("CHANGELOG.md is missing.")

    gradle_properties_path = ROOT_DIR / "gradle.properties"
    if not gradle_properties_path.exists():
        fail("gradle.properties is missing.")

    branch_result = run(
        "git", "symbolic-ref", "--quiet", "--short", "HEAD", check=False
    )
    branch = branch_result.stdout.strip()
    if branch_result.returncode != 0 or not branch:
        fail("Release must be created from a branch, not detached HEAD.")

    upstream = run(
        "git", "for-each-ref", "--format=%(upstream:short)", f"refs/heads/{branch}"
    ).stdout.strip()
    if not upstream:
        fail(f"Branch '{branch}' has no upstream configured. Set one before releasing.")
    remote = upstream.split("/", 1)[0]

    if run("git", "status", "--porcelain", "--untracked-files=all").stdout.strip():
        fail(
            "Working tree is not clean. Commit, stash, or remove changes before releasing."
        )

    run("git", "fetch", remote, "--tags")

    if (
        run(
            "git", "rev-parse", "-q", "--verify", f"refs/tags/{tag}", check=False
        ).returncode
        == 0
    ):
        fail(f"Tag '{tag}' already exists locally.")

    if (
        run(
            "git",
            "ls-remote",
            "--exit-code",
            "--tags",
            remote,
            f"refs/tags/{tag}",
            check=False,
        ).returncode
        == 0
    ):
        fail(f"Tag '{tag}' already exists on remote '{remote}'.")

    current_version = read_current_version(gradle_properties_path)
    if current_version == version:
        fail(f"Version is already {version}.")

    current_changelog = changelog_path.read_text(encoding="utf-8")
    current_gradle_properties = gradle_properties_path.read_text(encoding="utf-8")
    next_changelog = render_changelog(current_changelog, version, current_version)
    next_gradle_properties = render_gradle_version(current_gradle_properties, version)

    if check_mode:
        print(f"Release check for {version} succeeded.")
        print("Would:")
        print(f"- bump version {current_version} -> {version}")
        print(f"- move current Unreleased notes into {version}")
        print(f"- update [Unreleased] -> compare/{tag}...HEAD")
        print(f"- add [{version}] -> compare/v{current_version}...{tag}")
        print(f"- create commit Release {version}")
        print(f"- create annotated tag {tag}")
        print("- run ./gradlew clean build publishToCentralPortal")
        print(f"- push branch {branch} and tag {tag} to {remote}")
        print()
        print_diff(
            gradle_properties_path, current_gradle_properties, next_gradle_properties
        )
        print_diff(changelog_path, current_changelog, next_changelog)
        return

    gradle_properties_path.write_text(next_gradle_properties, encoding="utf-8")
    changelog_path.write_text(next_changelog, encoding="utf-8")

    commit_created = False
    tag_created = False
    publish_succeeded = False
    branch_pushed = False

    try:
        run("git", "add", "gradle.properties", "CHANGELOG.md")
        run("git", "commit", "-m", f"Release {version}")
        commit_created = True
        run("git", "tag", "-a", tag, "-m", f"Release {version}")
        tag_created = True

        subprocess.run(
            ["./gradlew", "clean", "build", "publishToCentralPortal"],
            cwd=ROOT_DIR,
            check=True,
        )
        publish_succeeded = True

        run("git", "push", remote, branch)
        branch_pushed = True
        run("git", "push", remote, tag)
    except subprocess.CalledProcessError as error:
        if publish_succeeded:
            print(
                "Release was published, but pushing git refs failed.", file=sys.stderr
            )
            if not branch_pushed:
                print(f"Push manually: git push {remote} {branch}", file=sys.stderr)
            print(f"Push manually: git push {remote} {tag}", file=sys.stderr)
        elif commit_created or tag_created:
            print(file=sys.stderr)
            print("Release failed after creating local git state.", file=sys.stderr)
            if tag_created:
                print(f"To remove the local tag: git tag -d {tag}", file=sys.stderr)
            if commit_created:
                print(
                    "To reset the release commit: git reset --hard HEAD~1",
                    file=sys.stderr,
                )
        raise SystemExit(error.returncode) from error

    print(f"Release {version} published successfully.")
    print("Pushed:")
    print(f"- branch: {branch}")
    print(f"- tag:    {tag}")


if __name__ == "__main__":
    main()

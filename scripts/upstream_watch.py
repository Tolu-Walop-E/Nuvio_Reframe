#!/usr/bin/env python3
"""Score NuvioMedia/NuvioTV commits that landed after this Reframe fork point.

Not a merge bot. Upstream and Reframe have diverged (Netflix home, trailers,
Coil). This prints a ranked list so a human or agent can port the few that
still apply.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
UPSTREAM_URL = "https://github.com/NuvioMedia/NuvioTV.git"
UPSTREAM_REF_CANDIDATES = ("nuvio/dev", "origin/dev")

HIGH_RE = re.compile(
    r"\b(perf|performance|coil|prefetch|memory|ram|gc|stutter|"
    r"compositing|parallel|okhttp|bitmap|cache improvement|"
    r"loading optimization|home perfromance|home performance)\b",
    re.I,
)
MEDIUM_RE = re.compile(
    r"\b(trailer|player|subtitle|tmdb|enrich|watched|continue.?watching|"
    r"poster|exo|mpv|home|catalog|debounce|hardware)\b",
    re.I,
)
NOISE_RE = re.compile(
    r"^(bump version|merge pull request|merge branch|feat\(i18n\)|"
    r"chore\(i18n\)|add missing translations|update strings\.xml)",
    re.I,
)
# Reframe already solved these locally; cherry-picking usually fights us.
CONFLICT_RE = re.compile(
    r"high-?res(olution)? trailer|restore high-resolution trailer|allowHardware",
    re.I,
)


@dataclass(frozen=True)
class Commit:
    sha: str
    date: str
    subject: str
    bucket: str


def git(*args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if check and result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def ensure_upstream() -> str:
    remotes = git("remote", "-v")
    names = {line.split()[0] for line in remotes.splitlines() if line.strip()}
    if "nuvio" not in names:
        origin = git("remote", "get-url", "origin", check=False)
        if "NuvioMedia/NuvioTV" in origin:
            return "origin"
        git("remote", "add", "nuvio", UPSTREAM_URL)
    git("fetch", "nuvio", "dev", "--prune", check=False)
    git("fetch", "origin", "dev", "--prune", check=False)
    for ref in UPSTREAM_REF_CANDIDATES:
        exists = git("rev-parse", "--verify", ref, check=False)
        if exists:
            return ref
    raise RuntimeError("Could not find nuvio/dev or origin/dev")


def score(subject: str) -> str:
    if CONFLICT_RE.search(subject):
        return "conflict"
    if NOISE_RE.search(subject):
        return "noise"
    if HIGH_RE.search(subject):
        return "high"
    if MEDIUM_RE.search(subject):
        return "medium"
    return "low"


def list_commits(upstream_ref: str) -> tuple[str, list[Commit]]:
    merge_base = git("merge-base", "HEAD", upstream_ref)
    log = git(
        "log",
        "--format=%h\t%cs\t%s",
        f"{merge_base}..{upstream_ref}",
    )
    commits: list[Commit] = []
    if log:
        for line in log.splitlines():
            sha, date, subject = line.split("\t", 2)
            commits.append(Commit(sha, date, subject, score(subject)))
    return merge_base, commits


def render(merge_base: str, upstream_ref: str, commits: list[Commit]) -> str:
    by_bucket: dict[str, list[Commit]] = {
        "high": [],
        "medium": [],
        "conflict": [],
        "low": [],
        "noise": [],
    }
    for commit in commits:
        by_bucket[commit.bucket].append(commit)

    lines = [
        f"Upstream: {upstream_ref} ({git('rev-parse', '--short', upstream_ref)})",
        f"Fork point / merge-base: {merge_base[:12]}",
        f"Commits not in HEAD: {len(commits)}",
        "",
        "Scoring is keyword-based, not a guarantee. High = likely home/Coil/"
        "prefetch/perf. Conflict = Reframe already took a different path"
        " (especially trailers).",
        "",
    ]
    titles = {
        "high": "HIGH - port next if the files still exist here",
        "medium": "MEDIUM - relevant player/home/metadata, review before taking",
        "conflict": "CONFLICT - do not cherry-pick blindly",
        "low": "LOW",
        "noise": "NOISE - version bumps, merges, i18n",
    }
    for bucket in ("high", "medium", "conflict", "low", "noise"):
        items = by_bucket[bucket]
        lines.append(f"## {titles[bucket]} ({len(items)})")
        if not items:
            lines.append("(none)")
        else:
            for commit in items:
                lines.append(f"- `{commit.sha}` {commit.date} {commit.subject}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--markdown",
        type=Path,
        help="Write the report to this file as well as stdout",
    )
    parser.add_argument(
        "--fail-on-high",
        action="store_true",
        help="Exit 2 when any HIGH commits are waiting",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    upstream_ref = ensure_upstream()
    merge_base, commits = list_commits(upstream_ref)
    report = render(merge_base, upstream_ref, commits)
    sys.stdout.write(report)
    if args.markdown:
        args.markdown.write_text(report, encoding="utf-8")
    high = sum(1 for commit in commits if commit.bucket == "high")
    if args.fail_on_high and high:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

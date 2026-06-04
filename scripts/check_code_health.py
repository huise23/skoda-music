#!/usr/bin/env python3
"""Simple project code-health guardrail.

Checks file line counts and approximate function/method lengths using
.ai/code_health_config.json. The parser is intentionally conservative and is
meant to flag review risk, not replace language-specific linters.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / ".ai" / "code_health_config.json"


DEFAULT_CONFIG = {
    "generalFile": {"preferred": 500, "warning": 800, "refactor": 1200, "red": 2000},
    "entryFile": {"preferred": 500, "warning": 800, "refactor": 1200, "red": 1800},
    "declarativeUiFile": {"preferred": 700, "warning": 1200, "refactor": 1800, "red": 2500},
    "testFile": {"preferred": 800, "warning": 1500, "refactor": 2500, "red": 4000},
    "function": {"preferred": 60, "warning": 120, "refactor": 200, "red": 300},
    "ignoreDirs": [".git", ".gradle", "build", "app/build", "dist", "KugouMusic.NET"],
    "ignoreFiles": [],
    "sourceExtensions": [".kt", ".java", ".kts", ".cpp", ".h", ".xml", ".qml"],
    "entryFilePatterns": ["MainActivity.kt", "Activity.kt", "Fragment.kt", "Service.kt", "Receiver.kt"],
    "declarativeUiPatterns": ["app/src/main/res/layout/", "app/src/main/res/drawable/", "src/ui/qml/"],
    "testPatterns": ["/test/", "/androidTest/", "Test.kt", "Test.java"],
    "failOnWarning": False,
    "failOnRefactor": False,
    "checkFunctions": True,
}


FUNCTION_START_RE = re.compile(
    r"""^\s*(?:
        (?:public|private|protected|internal|static|final|override|suspend|inline|open|fun)\s+
    )*
    (?:
        fun\s+[A-Za-z_][\w<>]*\s*\([^)]*\)\s*(?::\s*[^{=]+)?\{ |
        [A-Za-z_][\w:<>,\s*&~]*\s+[A-Za-z_]\w*\s*\([^;{}]*\)\s*(?:const\s*)?\{
    )""",
    re.VERBOSE,
)


@dataclass(frozen=True)
class Finding:
    level: str
    path: str
    message: str


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        return DEFAULT_CONFIG
    with CONFIG_PATH.open("r", encoding="utf-8") as fh:
        loaded = json.load(fh)
    config = dict(DEFAULT_CONFIG)
    config.update(loaded)
    return config


def as_posix_relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def is_ignored(path: Path, config: dict) -> bool:
    rel = as_posix_relative(path)
    parts = set(Path(rel).parts)
    for ignored in config.get("ignoreDirs", []):
        normalized = ignored.strip("/").replace("\\", "/")
        if not normalized:
            continue
        if rel == normalized or rel.startswith(normalized + "/") or normalized in parts:
            return True
    return path.name in set(config.get("ignoreFiles", []))


def iter_source_files(config: dict) -> Iterable[Path]:
    extensions = set(config.get("sourceExtensions", []))
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in extensions:
            continue
        if is_ignored(path, config):
            continue
        yield path


def category_for(path: Path, config: dict) -> str:
    rel = as_posix_relative(path)
    if any(pattern in rel for pattern in config.get("testPatterns", [])):
        return "testFile"
    if any(pattern in rel for pattern in config.get("declarativeUiPatterns", [])):
        return "declarativeUiFile"
    if any(rel.endswith(pattern) or path.name == pattern for pattern in config.get("entryFilePatterns", [])):
        return "entryFile"
    return "generalFile"


def level_for(count: int, thresholds: dict) -> str | None:
    if count > thresholds["red"]:
        return "red"
    if count > thresholds["refactor"]:
        return "refactor"
    if count > thresholds["warning"]:
        return "warning"
    return None


def count_lines(path: Path) -> int:
    try:
        with path.open("r", encoding="utf-8", errors="ignore") as fh:
            return sum(1 for _ in fh)
    except OSError:
        return 0


def function_findings(path: Path, config: dict) -> list[Finding]:
    if path.suffix not in {".kt", ".kts", ".java", ".c", ".cc", ".cpp", ".cxx", ".h", ".hpp", ".hh"}:
        return []
    try:
        lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return []

    findings: list[Finding] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if not FUNCTION_START_RE.match(line):
            index += 1
            continue

        start = index
        brace_depth = line.count("{") - line.count("}")
        index += 1
        while index < len(lines) and brace_depth > 0:
            brace_depth += lines[index].count("{") - lines[index].count("}")
            index += 1
        length = index - start
        level = level_for(length, config["function"])
        if level:
            rel = as_posix_relative(path)
            findings.append(Finding(level, rel, f"function/method near line {start + 1} has {length} lines"))
    return findings


def should_fail(level: str, args: argparse.Namespace) -> bool:
    if level == "red":
        return True
    if level == "refactor" and args.fail_on_refactor:
        return True
    if level == "warning" and args.fail_on_warning:
        return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Check code-health size guardrails.")
    parser.add_argument("--fail-on-warning", action="store_true", help="Fail on warning-level findings.")
    parser.add_argument("--fail-on-refactor", action="store_true", help="Fail on refactor-level findings.")
    parser.add_argument("--no-functions", action="store_true", help="Skip rough function/method length checks.")
    args = parser.parse_args()

    config = load_config()
    args.fail_on_warning = args.fail_on_warning or bool(config.get("failOnWarning", False))
    args.fail_on_refactor = args.fail_on_refactor or bool(config.get("failOnRefactor", False))
    check_functions = bool(config.get("checkFunctions", True)) and not args.no_functions

    findings: list[Finding] = []
    for path in sorted(iter_source_files(config)):
        category = category_for(path, config)
        line_count = count_lines(path)
        level = level_for(line_count, config[category])
        if level:
            rel = as_posix_relative(path)
            findings.append(Finding(level, rel, f"{category} has {line_count} lines"))
        if check_functions:
            findings.extend(function_findings(path, config))

    if not findings:
        print("Code health check passed: no size findings.")
        return 0

    order = {"red": 0, "refactor": 1, "warning": 2}
    for finding in sorted(findings, key=lambda item: (order[item.level], item.path, item.message)):
        print(f"{finding.level.upper()}: {finding.path}: {finding.message}")

    failing = [finding for finding in findings if should_fail(finding.level, args)]
    if failing:
        print(f"Code health check failed: {len(failing)} blocking finding(s).", file=sys.stderr)
        return 1

    print("Code health check passed with non-blocking findings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

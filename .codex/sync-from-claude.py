#!/usr/bin/env python3
"""Generate Codex sub-agent definitions from Claude sub-agent files."""

from __future__ import annotations

import argparse
import ast
import re
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
CLAUDE_AGENTS_DIR = REPO_ROOT / ".claude" / "agents"
CODEX_AGENTS_DIR = REPO_ROOT / ".codex" / "agents"
CODEX_MEMORY_DIR = REPO_ROOT / ".codex" / "agent-memory"

MEMORY_PATH_RE = re.compile(
    r"/Users/kwp/Desktop/Workspace/[^`\s]+/\.(?:claude|codex|Codex)/agent-memory/([A-Za-z0-9_-]+)/"
)


def parse_frontmatter(path: Path) -> tuple[dict[str, str], str]:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    if not lines or lines[0].strip() != "---":
        raise ValueError(f"{path} does not start with YAML frontmatter")

    end_index = None
    for index, line in enumerate(lines[1:], start=1):
        if line.strip() == "---":
            end_index = index
            break

    if end_index is None:
        raise ValueError(f"{path} has no closing YAML frontmatter marker")

    metadata: dict[str, str] = {}
    for line in lines[1:end_index]:
        if not line.strip() or ":" not in line:
            continue
        key, raw_value = line.split(":", 1)
        value = raw_value.strip()
        if value.startswith(("'", '"')):
            try:
                metadata[key.strip()] = ast.literal_eval(value)
                continue
            except (SyntaxError, ValueError):
                pass
        metadata[key.strip()] = value

    body = "".join(lines[end_index + 1 :]).lstrip("\n")
    return metadata, body


def toml_literal(value: str) -> str:
    if "'''" in value:
        escaped = (
            value.replace("\\", "\\\\")
            .replace('"', '\\"')
            .replace("\n", "\\n")
        )
        return f'"{escaped}"'
    return f"'''\n{value}\n'''"


def toml_basic_string(value: str) -> str:
    escaped = (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
    )
    return f'"{escaped}"'


def normalize_for_codex(body: str, agent_name: str, source_name: str) -> str:
    memory_root = f"{REPO_ROOT}/.codex/agent-memory/"
    body = MEMORY_PATH_RE.sub(lambda match: f"{memory_root}{match.group(1)}/", body)
    body = body.replace(
        "This directory already exists - write to it directly with the Write tool "
        "(do not run mkdir or check for its existence).",
        "This directory is managed by `.codex/sync-from-claude.py`; write to it "
        "with the available file-editing tool when memory is needed.",
    )
    body = body.replace(
        "Anything already documented in CLAUDE.md files.",
        "Anything already documented in AGENTS.md, CLAUDE.md, agent_rules, or docs/design files.",
    )

    runtime_context = f"""## Codex Runtime Context

This Codex agent is generated from `.claude/agents/{source_name}`. Treat the Claude agent file as the source of truth and regenerate this TOML with `.codex/sync-from-claude.py`.

At task start, follow `AGENTS.md`; it delegates to `CLAUDE.md`, `agent_rules/`, and `docs/design/` by task type. If those documents conflict with code or with each other, stop and ask the user before choosing a side.

Agent memory for this role belongs under `.codex/agent-memory/{agent_name}/`.
"""
    return f"{runtime_context}\n{body}"


def render_agent(source_path: Path) -> tuple[str, str]:
    metadata, body = parse_frontmatter(source_path)
    agent_name = metadata.get("name") or source_path.stem
    description = metadata.get("description")
    if not description:
        raise ValueError(f"{source_path} is missing required description")

    normalized_body = normalize_for_codex(body, agent_name, source_path.name)
    content = "\n".join(
        [
            f"# Generated from .claude/agents/{source_path.name}.",
            "# Edit the Claude source file, then run `.codex/sync-from-claude.py`.",
            f"name = {toml_basic_string(agent_name)}",
            f"description = {toml_literal(description)}",
            f"developer_instructions = {toml_literal(normalized_body)}",
            "",
        ]
    )
    return agent_name, content


def expected_agents() -> dict[Path, str]:
    if not CLAUDE_AGENTS_DIR.exists():
        raise FileNotFoundError(f"Missing source directory: {CLAUDE_AGENTS_DIR}")

    expected: dict[Path, str] = {}
    for source_path in sorted(CLAUDE_AGENTS_DIR.glob("*.md")):
        agent_name, content = render_agent(source_path)
        expected[CODEX_AGENTS_DIR / f"{agent_name}.toml"] = content
    return expected


def ensure_memory_indexes(agent_names: list[str]) -> None:
    for agent_name in agent_names:
        memory_dir = CODEX_MEMORY_DIR / agent_name
        memory_dir.mkdir(parents=True, exist_ok=True)
        memory_index = memory_dir / "MEMORY.md"
        if not memory_index.exists():
            memory_index.write_text("# Memory Index\n\n", encoding="utf-8")


def write_agents(expected: dict[Path, str]) -> list[Path]:
    CODEX_AGENTS_DIR.mkdir(parents=True, exist_ok=True)
    changed: list[Path] = []
    for target_path, content in expected.items():
        if not target_path.exists() or target_path.read_text(encoding="utf-8") != content:
            target_path.write_text(content, encoding="utf-8")
            changed.append(target_path)
    ensure_memory_indexes([path.stem for path in expected])
    return changed


def check_agents(expected: dict[Path, str]) -> list[Path]:
    drifted: list[Path] = []
    for target_path, content in expected.items():
        if not target_path.exists() or target_path.read_text(encoding="utf-8") != content:
            drifted.append(target_path)
    return drifted


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify generated Codex agent files are current without writing",
    )
    args = parser.parse_args()

    expected = expected_agents()
    if args.check:
        drifted = check_agents(expected)
        if drifted:
            print("Codex agent files are out of sync:", file=sys.stderr)
            for path in drifted:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python3 .codex/sync-from-claude.py", file=sys.stderr)
            return 1
        print("Codex agent files are in sync.")
        return 0

    changed = write_agents(expected)
    if changed:
        print("Updated Codex agent files:")
        for path in changed:
            print(f"  {path.relative_to(REPO_ROOT)}")
    else:
        print("Codex agent files are already in sync.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

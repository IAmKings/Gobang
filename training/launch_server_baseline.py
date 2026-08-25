#!/usr/bin/env python3
"""Validate and launch the pinned upstream 15x15 server training baseline."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

REQUIRED_BASELINE = {"board_size": 15, "num_channels": 64}
PROFILE_NAMES = ("smoke", "pilot", "production")


def load_yaml(path: Path) -> dict[str, Any]:
    try:
        import yaml
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "PyYAML is required for server launch validation; install training/server-requirements.txt"
        ) from exc
    with path.open(encoding="utf-8") as source:
        value = yaml.safe_load(source)
    if not isinstance(value, dict):
        raise ValueError("server config root must be a mapping")
    return value


def validate_config(config: dict[str, Any], profile: str) -> None:
    if profile not in PROFILE_NAMES:
        raise ValueError(f"profile must be one of: {', '.join(PROFILE_NAMES)}")
    baseline = config.get("baseline")
    if not isinstance(baseline, dict):
        raise ValueError("config.baseline must be a mapping")
    for key, expected in REQUIRED_BASELINE.items():
        if baseline.get(key) != expected:
            raise ValueError(f"baseline.{key} must be {expected}")
    source = config.get("source")
    if not isinstance(source, dict) or not source.get("commit"):
        raise ValueError("source.commit must pin the upstream training source")
    profiles = config.get("profiles")
    settings = profiles.get(profile) if isinstance(profiles, dict) else None
    if not isinstance(settings, dict):
        raise ValueError(f"profiles.{profile} must be a mapping")
    for key in (
        "epochs", "batch_size", "num_iterations", "num_episodes",
        "max_queue_length", "num_iters_history", "arena_compare",
        "temp_threshold", "num_sims",
    ):
        if not isinstance(settings.get(key), int) or settings[key] <= 0:
            raise ValueError(f"profiles.{profile}.{key} must be a positive integer")


def build_upstream_config(config: dict[str, Any], profile: str, checkpoint_dir: Path) -> dict[str, Any]:
    validate_config(config, profile)
    baseline = config["baseline"]
    settings = config["profiles"][profile]
    return {
        "training": {
            "epochs": settings["epochs"], "batch_size": settings["batch_size"],
            "num_iterations": settings["num_iterations"], "num_episodes": settings["num_episodes"],
            "max_queue_length": settings["max_queue_length"],
            "num_iters_history": settings["num_iters_history"],
            "update_threshold": settings.get("update_threshold", 0.55),
            "arena_compare": settings["arena_compare"], "temp_threshold": settings["temp_threshold"],
        },
        "network": {
            "num_channels": baseline["num_channels"], "dropout": baseline["dropout"],
            "learning_rate": {"min": baseline["min_lr"], "max": baseline["max_lr"]},
            "grad_clip": baseline["grad_clip"],
        },
        "mcts": {"num_sims": settings["num_sims"], "cpuct": baseline["cpuct"]},
        "game": {"board_size": baseline["board_size"]},
        "system": {
            "cuda": True, "checkpoint_dir": str(checkpoint_dir), "load_model": False,
            "load_folder_file": [str(checkpoint_dir), "best.pth.tar"],
        },
    }


def command_for(source_dir: Path, generated_config: Path, python_executable: str) -> list[str]:
    entrypoint = source_dir / "alphazero.py"
    if not entrypoint.is_file():
        raise FileNotFoundError(f"upstream entrypoint not found: {entrypoint}")
    return [python_executable, str(entrypoint), "--train", "--config", str(generated_config)]


def verify_source_commit(source_dir: Path, expected_commit: str) -> None:
    result = subprocess.run(
        ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
        check=False,
        capture_output=True,
        text=True,
    )
    actual_commit = result.stdout.strip()
    if result.returncode != 0 or actual_commit != expected_commit:
        raise ValueError(
            f"upstream source commit mismatch: expected {expected_commit}, got {actual_commit or 'unknown'}"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("server-baseline.yaml"))
    parser.add_argument("--profile", choices=PROFILE_NAMES, default="pilot")
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--checkpoint-dir", type=Path, required=True)
    parser.add_argument("--generated-config", type=Path)
    parser.add_argument("--python", default=sys.executable, dest="python_executable")
    parser.add_argument("--run", action="store_true", help="execute training; default only prints the command")
    args = parser.parse_args(argv)
    try:
        config = load_yaml(args.config)
        verify_source_commit(args.source_dir, config["source"]["commit"])
        generated_path = args.generated_config or args.checkpoint_dir / f"upstream-{args.profile}.yaml"
        upstream_config = build_upstream_config(config, args.profile, args.checkpoint_dir)
        generated_path.parent.mkdir(parents=True, exist_ok=True)
        import yaml
        generated_path.write_text(yaml.safe_dump(upstream_config, sort_keys=False), encoding="utf-8")
        command = command_for(args.source_dir, generated_path, args.python_executable)
        print(json.dumps({"profile": args.profile, "source_commit": config["source"]["commit"], "command": command}, indent=2))
        if not args.run:
            print("dry-run: pass --run to execute the command")
            return 0
        environment = os.environ.copy()
        environment.setdefault("SDL_VIDEODRIVER", "dummy")
        return subprocess.run(command, cwd=args.source_dir, env=environment, check=False).returncode
    except (FileNotFoundError, ModuleNotFoundError, RuntimeError, ValueError) as exc:
        print(f"server baseline launch failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

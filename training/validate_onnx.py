#!/usr/bin/env python3
"""Validate desktop ONNX Runtime outputs against generated golden cases."""

from __future__ import annotations

import argparse
import importlib
import json
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from training.contract import ACTION_SIZE, BOARD_SIZE, validate_manifest


def _require_ort() -> Any:
    try:
        return importlib.import_module("onnxruntime")
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "onnxruntime is required for desktop validation. Install it with "
            "`python3 -m pip install -r training/requirements.txt`."
        ) from exc


def _max_abs(actual: list[float], expected: list[float]) -> float:
    if len(actual) != len(expected):
        raise ValueError(f"output length mismatch: actual={len(actual)}, expected={len(expected)}")
    return max((abs(a - b) for a, b in zip(actual, expected)), default=0.0)


def _outputs(session: Any, board: list[list[int]]) -> tuple[list[float], list[float]]:
    inputs = session.get_inputs()
    if len(inputs) != 1:
        raise RuntimeError(f"expected one model input, found {len(inputs)}")
    model_input = [[[[float(cell) for cell in row] for row in board]]]
    policy, value = session.run(["policy", "value"], {inputs[0].name: model_input})
    return list(policy[0]), list(value[0])


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--max-abs-error", type=float, default=1e-4)
    args = parser.parse_args(argv)
    try:
        ort = _require_ort()
        data = json.loads(args.golden.read_text(encoding="utf-8"))
        if data.get("board_size") != BOARD_SIZE or data.get("action_size") != ACTION_SIZE:
            raise ValueError("golden file has an incompatible board/action contract")
        if args.manifest:
            manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
            errors = validate_manifest(manifest, args.model)
            if errors:
                raise ValueError("invalid manifest: " + "; ".join(errors))
        session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
        worst_policy = 0.0
        worst_value = 0.0
        failures = []
        for case in data.get("cases", []):
            actual_policy, actual_value = _outputs(session, case["input"])
            policy_error = _max_abs(actual_policy, case["policy"])
            value_error = _max_abs(actual_value, case["value"])
            worst_policy = max(worst_policy, policy_error)
            worst_value = max(worst_value, value_error)
            legal = case["legal_mask"]
            legal_argmax = max((i for i, allowed in enumerate(legal) if allowed), key=lambda i: (actual_policy[i], -i), default=-1)
            if legal_argmax != case["expected_legal_argmax"] or policy_error > args.max_abs_error or value_error > args.max_abs_error:
                failures.append((case["name"], policy_error, value_error, legal_argmax, case["expected_legal_argmax"]))
        print(f"validated {len(data.get('cases', []))} cases")
        print(f"worst policy max_abs_error={worst_policy:.6g}, value={worst_value:.6g}")
        if failures:
            for failure in failures[:5]:
                print(f"failure: {failure}", file=sys.stderr)
            return 1
        return 0
    except (FileNotFoundError, KeyError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(f"ONNX validation failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

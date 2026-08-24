#!/usr/bin/env python3
"""Generate deterministic model golden cases from a 15x15 checkpoint."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from training.contract import ACTION_SIZE, BOARD_SIZE, expected_legal_argmax, fixture_boards, write_json
from training.original_model import CheckpointError, DependencyError, load_checkpoint


def _predict(torch, model, board: list[list[int]]) -> tuple[list[float], list[float]]:
    tensor = torch.tensor([[board]], dtype=torch.float32)
    with torch.no_grad():
        log_policy, value = model(tensor)
    policy = torch.exp(log_policy).detach().cpu().reshape(-1).tolist()
    value_list = value.detach().cpu().reshape(-1).tolist()
    return policy, value_list


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--num-channels", type=int, default=512)
    parser.add_argument("--device", choices=("auto", "cpu", "mps", "cuda"), default="auto")
    parser.add_argument("--seed", type=int, default=20260825)
    parser.add_argument("--random-cases", type=int, default=8)
    args = parser.parse_args(argv)
    try:
        torch, model, device = load_checkpoint(
            args.checkpoint,
            num_channels=args.num_channels,
            requested_device=args.device,
        )
        cases = []
        for case in fixture_boards(args.seed, args.random_cases):
            policy, value = _predict(torch, model, case["input"])
            if len(policy) != ACTION_SIZE or len(value) != 1:
                raise RuntimeError(f"model output shape mismatch: policy={len(policy)}, value={len(value)}")
            case.update(
                {
                    "policy": policy,
                    "value": value,
                    "expected_legal_argmax": expected_legal_argmax(policy, case["legal_mask"]),
                }
            )
            cases.append(case)
        write_json(
            args.output,
            {
                "contract_version": 1,
                "board_size": BOARD_SIZE,
                "action_size": ACTION_SIZE,
                "device": str(device),
                "seed": args.seed,
                "cases": cases,
            },
        )
        print(f"wrote {args.output} ({len(cases)} cases)")
        return 0
    except (CheckpointError, DependencyError, FileNotFoundError, RuntimeError, ValueError) as exc:
        print(f"golden generation failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

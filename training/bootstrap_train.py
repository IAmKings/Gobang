#!/usr/bin/env python3
"""Train a tiny deterministic 15x15 bootstrap checkpoint for contract smoke tests.

This is supervised tactical bootstrap data, not AlphaZero self-play. The output
must only be used to exercise checkpoint loading, ONNX export, and Android ORT;
it is not a production-strength model.
"""

from __future__ import annotations

import argparse
import random
import sys
from dataclasses import dataclass
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from training.original_model import choose_device, create_original_model, require_torch

BOARD_SIZE = 15
ACTION_SIZE = BOARD_SIZE * BOARD_SIZE
DEFAULT_SEED = 20260825


@dataclass(frozen=True)
class BootstrapCase:
    board: tuple[float, ...]
    target_action: int
    target_value: float


def _line_start(direction: tuple[int, int], rng: random.Random) -> tuple[int, int]:
    dr, dc = direction
    valid: list[tuple[int, int]] = []
    for row in range(BOARD_SIZE):
        for col in range(BOARD_SIZE):
            end_row = row + dr * 4
            end_col = col + dc * 4
            if 0 <= end_row < BOARD_SIZE and 0 <= end_col < BOARD_SIZE:
                valid.append((row, col))
    return rng.choice(valid)


def build_cases(seed: int = DEFAULT_SEED, count: int = 32) -> list[BootstrapCase]:
    """Create deterministic legal-shaped positions with an immediate win."""
    if count <= 0:
        raise ValueError("count must be positive")
    rng = random.Random(seed)
    directions = ((0, 1), (1, 0), (1, 1), (1, -1))
    cases: list[BootstrapCase] = []
    for _ in range(count):
        direction = rng.choice(directions)
        row, col = _line_start(direction, rng)
        dr, dc = direction
        board = [0.0] * ACTION_SIZE
        line = [(row + dr * offset, col + dc * offset) for offset in range(5)]
        target = line[rng.choice((0, 4))]
        line_indices = {stone_row * BOARD_SIZE + stone_col for stone_row, stone_col in line}
        for stone_row, stone_col in line:
            if (stone_row, stone_col) != target:
                board[stone_row * BOARD_SIZE + stone_col] = 1.0

        # Add a small amount of opponent context without touching the tactical line.
        for _ in range(rng.randint(2, 10)):
            stone_row = rng.randrange(BOARD_SIZE)
            stone_col = rng.randrange(BOARD_SIZE)
            index = stone_row * BOARD_SIZE + stone_col
            if index not in line_indices and board[index] == 0.0:
                board[index] = -1.0
        cases.append(
            BootstrapCase(
                board=tuple(board),
                target_action=target[0] * BOARD_SIZE + target[1],
                target_value=1.0,
            )
        )
    return cases


def train(
    output: Path,
    *,
    steps: int = 2,
    batch_size: int = 8,
    cases: int = 32,
    num_channels: int = 512,
    learning_rate: float = 1e-4,
    seed: int = DEFAULT_SEED,
    requested_device: str = "cpu",
) -> dict[str, object]:
    if steps <= 0 or batch_size <= 0 or cases <= 0:
        raise ValueError("steps, batch_size, and cases must be positive")
    torch = require_torch()
    random.seed(seed)
    torch.manual_seed(seed)
    device = choose_device(torch, requested_device)
    model = create_original_model(torch, BOARD_SIZE, num_channels).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    dataset = build_cases(seed, cases)
    boards = torch.tensor([case.board for case in dataset], dtype=torch.float32, device=device)
    targets = torch.tensor([case.target_action for case in dataset], dtype=torch.long, device=device)
    values = torch.tensor([case.target_value for case in dataset], dtype=torch.float32, device=device)

    model.train()
    losses: list[float] = []
    for step in range(steps):
        order = torch.randperm(len(dataset), device=device)
        step_loss = 0.0
        batches = 0
        for start in range(0, len(dataset), batch_size):
            indices = order[start : start + batch_size]
            if len(indices) < 2:
                continue  # BatchNorm1d needs at least two samples while training.
            log_policy, predicted_value = model(boards[indices])
            policy_loss = torch.nn.functional.nll_loss(log_policy, targets[indices])
            value_loss = torch.nn.functional.mse_loss(predicted_value.squeeze(1), values[indices])
            loss = policy_loss + value_loss
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            step_loss += float(loss.detach().cpu())
            batches += 1
        if batches == 0:
            raise ValueError("batch_size and cases must produce at least one full batch")
        losses.append(step_loss / batches)

    output.parent.mkdir(parents=True, exist_ok=True)
    state_dict = {name: value.detach().cpu() for name, value in model.state_dict().items()}
    checkpoint = {
        "state_dict": state_dict,
        "bootstrap_metadata": {
            "kind": "contract-smoke-only",
            "dataset": "deterministic-immediate-win-supervision",
            "board_size": BOARD_SIZE,
            "action_size": ACTION_SIZE,
            "num_channels": num_channels,
            "seed": seed,
            "steps": steps,
            "batch_size": batch_size,
            "cases": cases,
            "device": str(device),
            "losses": losses,
        },
    }
    torch.save(checkpoint, output)
    print(f"wrote bootstrap checkpoint {output}")
    print(f"device={device} steps={steps} cases={cases} final_loss={losses[-1]:.6f}")
    return checkpoint["bootstrap_metadata"]  # type: ignore[return-value]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--cases", type=int, default=32)
    parser.add_argument("--num-channels", type=int, default=512)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--device", choices=("auto", "cpu", "mps", "cuda"), default="cpu")
    args = parser.parse_args(argv)
    try:
        train(
            args.output,
            steps=args.steps,
            batch_size=args.batch_size,
            cases=args.cases,
            num_channels=args.num_channels,
            learning_rate=args.learning_rate,
            seed=args.seed,
            requested_device=args.device,
        )
        return 0
    except (RuntimeError, ValueError) as exc:
        print(f"bootstrap training failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

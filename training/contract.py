"""Pure-stdlib model contract and deterministic fixture helpers.

The Android engine consumes this contract directly: a 15x15 canonical board
with row-major actions, a probability policy, and a current-player value.
"""

from __future__ import annotations

import hashlib
import json
import random
from pathlib import Path
from typing import Any, Iterable

BOARD_SIZE = 15
ACTION_SIZE = BOARD_SIZE * BOARD_SIZE
RULES_VERSION = "gomoku-15-exact-five-v1"
CONTRACT_VERSION = 1
INPUT_SHAPE = [1, 1, BOARD_SIZE, BOARD_SIZE]
POLICY_SHAPE = [1, ACTION_SIZE]
VALUE_SHAPE = [1, 1]


def action(row: int, col: int) -> int:
    if not (0 <= row < BOARD_SIZE and 0 <= col < BOARD_SIZE):
        raise ValueError(f"row/col out of range: ({row}, {col})")
    return row * BOARD_SIZE + col


def row_col(index: int) -> tuple[int, int]:
    if not 0 <= index < ACTION_SIZE:
        raise ValueError(f"action out of range: {index}")
    return divmod(index, BOARD_SIZE)


def legal_mask(board: list[list[float]]) -> list[int]:
    validate_board(board)
    return [int(cell == 0) for row in board for cell in row]


def validate_board(board: list[list[float]]) -> None:
    if len(board) != BOARD_SIZE or any(len(row) != BOARD_SIZE for row in board):
        raise ValueError(f"board must be {BOARD_SIZE}x{BOARD_SIZE}")
    for row in board:
        for cell in row:
            if cell not in (-1, 0, 1):
                raise ValueError("canonical board cells must be -1, 0, or 1")


def validate_vector(values: list[float], expected: int, name: str) -> None:
    if len(values) != expected:
        raise ValueError(f"{name} must contain {expected} values, got {len(values)}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _empty() -> list[list[int]]:
    return [[0 for _ in range(BOARD_SIZE)] for _ in range(BOARD_SIZE)]


def _copy(board: list[list[int]]) -> list[list[int]]:
    return [row[:] for row in board]


def _canonical(board: list[list[int]], player: int) -> list[list[int]]:
    if player not in (-1, 1):
        raise ValueError("player must be -1 or 1")
    return [[cell * player for cell in row] for row in board]


def _has_five(board: list[list[int]], row: int, col: int, player: int) -> bool:
    for dr, dc in ((1, 0), (0, 1), (1, 1), (1, -1)):
        count = 1
        for direction in (-1, 1):
            r, c = row + dr * direction, col + dc * direction
            while 0 <= r < BOARD_SIZE and 0 <= c < BOARD_SIZE and board[r][c] == player:
                count += 1
                r += dr * direction
                c += dc * direction
        if count >= 5:
            return True
    return False


def _case(name: str, board: list[list[int]], description: str) -> dict[str, Any]:
    validate_board(board)
    return {
        "name": name,
        "description": description,
        "input": board,
        "legal_mask": legal_mask(board),
    }


def fixture_boards(seed: int = 20260825, random_cases: int = 8) -> list[dict[str, Any]]:
    """Return deterministic inputs without pretending to have model outputs."""
    empty = _empty()
    single_black = _empty()
    single_black[7][7] = 1
    single_white = _empty()
    single_white[7][7] = -1
    edge = _empty()
    edge[0][0] = 1
    edge[0][1] = -1
    edge[1][0] = -1
    edge[1][1] = 1
    terminal = _empty()
    for col in range(5):
        terminal[7][col] = 1

    cases = [
        _case("empty", empty, "empty board"),
        _case("single-black-center", single_black, "one current-player stone at center"),
        _case("single-white-center", single_white, "one opponent stone at center"),
        _case("edge-corners", edge, "stones touching the top-left corner"),
        _case("terminal-horizontal-five", terminal, "terminal five in a row"),
    ]

    rng = random.Random(seed)
    for case_index in range(random_cases):
        physical = _empty()
        player = 1
        moves = 4 + case_index * 3
        played = 0
        while played < moves:
            candidates = [(r, c) for r in range(BOARD_SIZE) for c in range(BOARD_SIZE) if physical[r][c] == 0]
            row, col = rng.choice(candidates)
            physical[row][col] = player
            played += 1
            if _has_five(physical, row, col, player):
                physical[row][col] = 0
                continue
            player *= -1
        cases.append(_case(f"random-{case_index:02d}", _canonical(physical, player), "seeded legal non-terminal position"))
    return cases


def expected_legal_argmax(policy: list[float], mask: list[int]) -> int:
    validate_vector(policy, ACTION_SIZE, "policy")
    validate_vector(mask, ACTION_SIZE, "legal_mask")
    candidates = [index for index, allowed in enumerate(mask) if allowed]
    if not candidates:
        return -1
    return max(candidates, key=lambda index: (policy[index], -index))


def build_manifest(
    model_path: Path,
    checkpoint_path: Path,
    *,
    model_version: str,
    opset: int,
    num_channels: int,
    device: str,
    seed: int,
) -> dict[str, Any]:
    if not model_path.is_file():
        raise FileNotFoundError(f"ONNX model does not exist: {model_path}")
    if not checkpoint_path.is_file():
        raise FileNotFoundError(f"checkpoint does not exist: {checkpoint_path}")
    return {
        "contract_version": CONTRACT_VERSION,
        "status": "exported",
        "model_version": model_version,
        "architecture": "Nagi-ovo/alphazero-gomoku:GomokuNNet",
        "board_size": BOARD_SIZE,
        "action_size": ACTION_SIZE,
        "action_order": "row-major",
        "rules_version": RULES_VERSION,
        "input": {
            "name": "canonical_board",
            "shape": INPUT_SHAPE,
            "dtype": "float32",
            "values": {"empty": 0, "current_player": 1, "opponent": -1},
        },
        "outputs": {
            "policy": {"name": "policy", "shape": POLICY_SHAPE, "dtype": "float32", "semantic": "probability"},
            "value": {"name": "value", "shape": VALUE_SHAPE, "dtype": "float32", "semantic": "current_player"},
        },
        "value_perspective": "current_player",
        "opset": opset,
        "num_channels": num_channels,
        "export_device": device,
        "seed": seed,
        "source_checkpoint_sha256": sha256_file(checkpoint_path),
        "model_sha256": sha256_file(model_path),
    }


def validate_manifest(manifest: dict[str, Any], model_path: Path | None = None) -> list[str]:
    errors: list[str] = []
    if manifest.get("status") != "exported":
        errors.append("manifest status is not exported")
    if manifest.get("board_size") != BOARD_SIZE:
        errors.append(f"board_size must be {BOARD_SIZE}")
    if manifest.get("action_size") != ACTION_SIZE:
        errors.append(f"action_size must be {ACTION_SIZE}")
    if manifest.get("action_order") != "row-major":
        errors.append("action_order must be row-major")
    if manifest.get("rules_version") != RULES_VERSION:
        errors.append(f"rules_version must be {RULES_VERSION}")
    if manifest.get("input", {}).get("shape") != INPUT_SHAPE:
        errors.append(f"input shape must be {INPUT_SHAPE}")
    if manifest.get("input", {}).get("values") != {"empty": 0, "current_player": 1, "opponent": -1}:
        errors.append("input values must be empty=0, current_player=1, opponent=-1")
    if manifest.get("outputs", {}).get("policy", {}).get("shape") != POLICY_SHAPE:
        errors.append(f"policy shape must be {POLICY_SHAPE}")
    if manifest.get("outputs", {}).get("value", {}).get("shape") != VALUE_SHAPE:
        errors.append(f"value shape must be {VALUE_SHAPE}")
    if model_path is not None and model_path.is_file() and manifest.get("model_sha256") != sha256_file(model_path):
        errors.append(f"model_sha256 does not match {model_path}")
    return errors

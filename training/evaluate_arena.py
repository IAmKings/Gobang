#!/usr/bin/env python3
"""Run a read-only, balanced AlphaZero Arena evaluation between checkpoints."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import math
import os
import sys
import tempfile
from pathlib import Path
from typing import Any

DEFAULT_GAMES = 200
DEFAULT_SIMULATIONS = 800
DEFAULT_PROMOTION_THRESHOLD = 0.55
WILSON_Z_95 = 1.959963984540054
REQUIRED_BASELINE = {"board_size": 15, "num_channels": 64}
RESUME_STATE_VERSION = 1


def fsync_parent(path: Path) -> None:
    """Best-effort directory sync after an atomic replace."""
    try:
        descriptor = os.open(path.parent, os.O_RDONLY)
    except OSError:
        return
    try:
        os.fsync(descriptor)
    except OSError:
        pass
    finally:
        os.close(descriptor)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as output:
            output.write(json.dumps(value, indent=2, sort_keys=True) + "\n")
            output.flush()
            os.fsync(output.fileno())
            temp_path = Path(output.name)
        temp_path.replace(path)
        fsync_parent(path)
    finally:
        if temp_path is not None and temp_path.exists():
            temp_path.unlink()


def verify_source_commit(source_dir: Path, expected_commit: str) -> None:
    import subprocess

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


def validate_games(games: int) -> None:
    if games <= 0 or games % 2:
        raise ValueError("games must be a positive even integer for balanced first-player evaluation")


def validate_contract(
    board_size: int,
    num_channels: int,
    cuda: bool,
    *,
    require_cuda: bool = True,
) -> None:
    if board_size != REQUIRED_BASELINE["board_size"]:
        raise ValueError(f"board_size must be {REQUIRED_BASELINE['board_size']}")
    if num_channels != REQUIRED_BASELINE["num_channels"]:
        raise ValueError(f"num_channels must be {REQUIRED_BASELINE['num_channels']}")
    if require_cuda and not cuda:
        raise ValueError("CUDA must be available for promotion evaluation")


def wilson_interval(wins: int, games: int, z: float = WILSON_Z_95) -> tuple[float, float] | None:
    if games == 0:
        return None
    if not 0 <= wins <= games:
        raise ValueError("wins must be between zero and games")
    proportion = wins / games
    denominator = 1 + z * z / games
    center = (proportion + z * z / (2 * games)) / denominator
    margin = z * math.sqrt((proportion * (1 - proportion) + z * z / (4 * games)) / games) / denominator
    return center - margin, center + margin


def promotion_decision(
    candidate_wins: int,
    baseline_wins: int,
    draws: int,
    threshold: float = DEFAULT_PROMOTION_THRESHOLD,
) -> dict[str, Any]:
    if not 0 < threshold <= 1:
        raise ValueError("promotion threshold must be in (0, 1]")
    decisive_games = candidate_wins + baseline_wins
    if decisive_games == 0:
        return {
            "status": "BLOCKED",
            "reason": "no decisive games",
            "candidate_win_rate": None,
            "wilson_95": None,
            "decisive_games": 0,
            "draws": draws,
        }
    candidate_win_rate = candidate_wins / decisive_games
    interval = wilson_interval(candidate_wins, decisive_games)
    assert interval is not None
    passed = candidate_win_rate >= threshold and interval[0] > 0.5
    return {
        "status": "PASS" if passed else "BLOCKED",
        "reason": "promotion criteria met" if passed else "promotion criteria not met",
        "candidate_win_rate": candidate_win_rate,
        "wilson_95": {"lower": interval[0], "upper": interval[1]},
        "decisive_games": decisive_games,
        "draws": draws,
    }


def validate_inputs(
    source_dir: Path,
    expected_commit: str,
    candidate: Path,
    baseline: Path,
    games: int,
    output: Path,
) -> None:
    validate_games(games)
    verify_source_commit(source_dir, expected_commit)
    for name, checkpoint in (("candidate", candidate), ("baseline", baseline)):
        if not checkpoint.is_file():
            raise FileNotFoundError(f"{name} checkpoint not found: {checkpoint}")
    if candidate.resolve() == baseline.resolve():
        raise ValueError("candidate and baseline checkpoints must differ")
    if output.exists():
        raise FileExistsError(f"refusing to overwrite evidence file: {output}")


def import_upstream(source_dir: Path) -> tuple[Any, Any]:
    source_text = str(source_dir.resolve())
    if not (source_dir / "alphazero.py").is_file() or not (source_dir / "game.py").is_file():
        raise FileNotFoundError(f"upstream AlphaZero source is incomplete: {source_dir}")
    sys.path.insert(0, source_text)
    try:
        importlib.invalidate_caches()
        return importlib.import_module("alphazero"), importlib.import_module("game")
    finally:
        sys.path.remove(source_text)


def build_resume_identity(
    source_commit: str,
    config_path: Path,
    candidate: Path,
    baseline: Path,
    games: int,
    simulations: int,
    threshold: float,
    metadata: dict[str, Any],
    local_precheck: bool,
) -> dict[str, Any]:
    return {
        "source_commit": source_commit,
        "config_sha256": sha256_file(config_path),
        "candidate_sha256": sha256_file(candidate),
        "baseline_sha256": sha256_file(baseline),
        "games": games,
        "simulations": simulations,
        "promotion_threshold": threshold,
        "board_size": metadata["board_size"],
        "num_channels": metadata["num_channels"],
        "cuda": metadata["cuda"],
        "local_precheck": local_precheck,
    }


def serialize_numpy_rng_state(np_module: Any) -> dict[str, Any]:
    algorithm, keys, position, has_gauss, cached_gaussian = np_module.random.get_state()
    return {
        "algorithm": algorithm,
        "keys": keys.tolist(),
        "position": position,
        "has_gauss": has_gauss,
        "cached_gaussian": cached_gaussian,
    }


def restore_numpy_rng_state(np_module: Any, state: dict[str, Any]) -> None:
    try:
        np_module.random.set_state(
            (
                str(state["algorithm"]),
                np_module.array(state["keys"], dtype="uint32"),
                int(state["position"]),
                int(state["has_gauss"]),
                float(state["cached_gaussian"]),
            )
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"invalid NumPy RNG state: {exc}") from exc


def new_resume_state(identity: dict[str, Any], np_module: Any) -> dict[str, Any]:
    return {
        "version": RESUME_STATE_VERSION,
        "identity": identity,
        "next_game_index": 0,
        "candidate_wins": 0,
        "baseline_wins": 0,
        "draws": 0,
        "numpy_rng_state": serialize_numpy_rng_state(np_module),
    }


def validate_resume_state(state: dict[str, Any], identity: dict[str, Any]) -> None:
    if state.get("version") != RESUME_STATE_VERSION:
        raise ValueError("resume state version is unsupported")
    if state.get("identity") != identity:
        raise ValueError("resume state inputs do not match this Arena evaluation")
    try:
        next_game_index = int(state["next_game_index"])
        candidate_wins = int(state["candidate_wins"])
        baseline_wins = int(state["baseline_wins"])
        draws = int(state["draws"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"resume state counters are invalid: {exc}") from exc
    if not 0 <= next_game_index <= identity["games"]:
        raise ValueError("resume state next_game_index is out of range")
    if min(candidate_wins, baseline_wins, draws) < 0:
        raise ValueError("resume state contains a negative score")
    if candidate_wins + baseline_wins + draws != next_game_index:
        raise ValueError("resume state score does not match completed game count")
    if not isinstance(state.get("numpy_rng_state"), dict):
        raise ValueError("resume state is missing NumPy RNG state")


def load_or_create_resume_state(
    state_path: Path,
    identity: dict[str, Any],
    np_module: Any,
) -> dict[str, Any]:
    if not state_path.exists():
        state = new_resume_state(identity, np_module)
        write_json(state_path, state)
        return state
    try:
        state = json.loads(state_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"resume state is not valid JSON: {exc}") from exc
    if not isinstance(state, dict):
        raise ValueError("resume state must be a JSON object")
    validate_resume_state(state, identity)
    restore_numpy_rng_state(np_module, state["numpy_rng_state"])
    return state


def record_game_result(state: dict[str, Any], candidate_result: int, np_module: Any) -> None:
    if candidate_result == 1:
        state["candidate_wins"] += 1
    elif candidate_result == -1:
        state["baseline_wins"] += 1
    elif candidate_result == 0:
        state["draws"] += 1
    else:
        raise ValueError(f"Arena returned invalid game result: {candidate_result}")
    state["next_game_index"] += 1
    state["numpy_rng_state"] = serialize_numpy_rng_state(np_module)


def run_arena(
    source_dir: Path,
    config_path: Path,
    candidate: Path,
    baseline: Path,
    games: int,
    simulations: int,
) -> tuple[int, int, int, dict[str, Any]]:
    if simulations <= 0:
        raise ValueError("simulations must be positive")
    upstream, game_module = import_upstream(source_dir)
    args = upstream.load_config(str(config_path))
    validate_contract(args.board_size, args.num_channels, args.cuda)
    args.numMCTSSims = simulations

    game = game_module.GomokuGame(args.board_size)
    candidate_network = upstream.NNetWrapper(game, args)
    baseline_network = upstream.NNetWrapper(game, args)
    candidate_network.load_checkpoint(str(candidate.parent), candidate.name)
    baseline_network.load_checkpoint(str(baseline.parent), baseline.name)

    candidate_mcts = upstream.MCTS(game, candidate_network, args)
    baseline_mcts = upstream.MCTS(game, baseline_network, args)
    arena = game_module.Arena(
        lambda board: int(upstream.np.argmax(candidate_mcts.getActionProb(board, temp=0))),
        lambda board: int(upstream.np.argmax(baseline_mcts.getActionProb(board, temp=0))),
        game,
    )
    candidate_wins, baseline_wins, draws = arena.playGames(games)
    metadata = {
        "board_size": args.board_size,
        "num_channels": args.num_channels,
        "cuda": args.cuda,
        "simulations": simulations,
    }
    return candidate_wins, baseline_wins, draws, metadata


def run_resumable_arena(
    source_dir: Path,
    source_commit: str,
    config_path: Path,
    candidate: Path,
    baseline: Path,
    games: int,
    simulations: int,
    threshold: float,
    state_path: Path,
    local_precheck: bool,
) -> tuple[int, int, int, dict[str, Any]]:
    if simulations <= 0:
        raise ValueError("simulations must be positive")
    upstream, game_module = import_upstream(source_dir)
    args = upstream.load_config(str(config_path))
    validate_contract(
        args.board_size,
        args.num_channels,
        args.cuda,
        require_cuda=not local_precheck,
    )
    args.numMCTSSims = simulations
    metadata = {
        "board_size": args.board_size,
        "num_channels": args.num_channels,
        "cuda": args.cuda,
        "simulations": simulations,
        "resumable": True,
        "local_precheck": local_precheck,
        "promotion_authority": not local_precheck,
        "resume_state_path": str(state_path),
    }
    identity = build_resume_identity(
        source_commit,
        config_path,
        candidate,
        baseline,
        games,
        simulations,
        threshold,
        metadata,
        local_precheck,
    )
    state = load_or_create_resume_state(state_path, identity, upstream.np)

    game = game_module.GomokuGame(args.board_size)
    candidate_network = upstream.NNetWrapper(game, args)
    baseline_network = upstream.NNetWrapper(game, args)
    candidate_network.load_checkpoint(str(candidate.parent), candidate.name)
    baseline_network.load_checkpoint(str(baseline.parent), baseline.name)

    first_player_games = games // 2
    while state["next_game_index"] < games:
        game_index = state["next_game_index"]
        candidate_mcts = upstream.MCTS(game, candidate_network, args)
        baseline_mcts = upstream.MCTS(game, baseline_network, args)

        def candidate_player(board: Any) -> int:
            return int(upstream.np.argmax(candidate_mcts.getActionProb(board, temp=0)))

        def baseline_player(board: Any) -> int:
            return int(upstream.np.argmax(baseline_mcts.getActionProb(board, temp=0)))

        candidate_first = game_index < first_player_games
        arena = game_module.Arena(
            candidate_player if candidate_first else baseline_player,
            baseline_player if candidate_first else candidate_player,
            game,
        )
        player_one_result = arena.playGame()
        candidate_result = player_one_result if candidate_first else -player_one_result
        record_game_result(state, candidate_result, upstream.np)
        write_json(state_path, state)
        print(
            json.dumps(
                {
                    "event": "arena-progress",
                    "completed_games": state["next_game_index"],
                    "games": games,
                    "candidate_wins": state["candidate_wins"],
                    "baseline_wins": state["baseline_wins"],
                    "draws": state["draws"],
                },
                sort_keys=True,
            ),
            flush=True,
        )

    if sha256_file(candidate) != identity["candidate_sha256"]:
        raise RuntimeError("candidate checkpoint changed during Arena evaluation")
    if sha256_file(baseline) != identity["baseline_sha256"]:
        raise RuntimeError("baseline checkpoint changed during Arena evaluation")
    return state["candidate_wins"], state["baseline_wins"], state["draws"], metadata


def build_evidence(
    source_dir: Path,
    source_commit: str,
    config_path: Path,
    candidate: Path,
    baseline: Path,
    games: int,
    threshold: float,
    candidate_wins: int,
    baseline_wins: int,
    draws: int,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    decision = promotion_decision(candidate_wins, baseline_wins, draws, threshold)
    return {
        "source_dir": str(source_dir),
        "source_commit": source_commit,
        "config_path": str(config_path),
        "candidate": {"path": str(candidate), "sha256": sha256_file(candidate)},
        "baseline": {"path": str(baseline), "sha256": sha256_file(baseline)},
        "games": games,
        "candidate_wins": candidate_wins,
        "baseline_wins": baseline_wins,
        "draws": draws,
        "promotion_threshold": threshold,
        **metadata,
        "decision": decision,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--games", type=int, default=DEFAULT_GAMES)
    parser.add_argument("--simulations", type=int, default=DEFAULT_SIMULATIONS)
    parser.add_argument("--promotion-threshold", type=float, default=DEFAULT_PROMOTION_THRESHOLD)
    parser.add_argument(
        "--resume-state",
        type=Path,
        help="atomically updated JSON state; enables per-game resumable evaluation",
    )
    parser.add_argument(
        "--local-precheck",
        action="store_true",
        help="allow a non-CUDA resumable run; evidence is explicitly non-authoritative",
    )
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        validate_inputs(
            args.source_dir,
            args.source_commit,
            args.candidate,
            args.baseline,
            args.games,
            args.output,
        )
        if args.local_precheck != (args.resume_state is not None):
            raise ValueError("--local-precheck and --resume-state must be supplied together")
        if args.dry_run:
            print(json.dumps({
                "mode": "dry-run",
                "source_dir": str(args.source_dir),
                "source_commit": args.source_commit,
                "candidate": str(args.candidate),
                "baseline": str(args.baseline),
                "output": str(args.output),
                "games": args.games,
                "simulations": args.simulations,
                "promotion_threshold": args.promotion_threshold,
                "resume_state": str(args.resume_state) if args.resume_state else None,
                "local_precheck": args.local_precheck,
            }, indent=2))
            return 0
        if args.resume_state:
            candidate_wins, baseline_wins, draws, metadata = run_resumable_arena(
                args.source_dir,
                args.source_commit,
                args.config,
                args.candidate,
                args.baseline,
                args.games,
                args.simulations,
                args.promotion_threshold,
                args.resume_state,
                args.local_precheck,
            )
        else:
            candidate_wins, baseline_wins, draws, metadata = run_arena(
                args.source_dir,
                args.config,
                args.candidate,
                args.baseline,
                args.games,
                args.simulations,
            )
        evidence = build_evidence(
            args.source_dir,
            args.source_commit,
            args.config,
            args.candidate,
            args.baseline,
            args.games,
            args.promotion_threshold,
            candidate_wins,
            baseline_wins,
            draws,
            metadata,
        )
        evidence["decision"]["authoritative"] = metadata.get("promotion_authority", True)
        write_json(args.output, evidence)
        print(json.dumps(evidence, indent=2, sort_keys=True))
        return 0
    except (FileNotFoundError, ImportError, ModuleNotFoundError, RuntimeError, ValueError) as exc:
        print(f"arena evaluation failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

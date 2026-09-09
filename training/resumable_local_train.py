#!/usr/bin/env python3
"""Interruption-safe local AlphaZero training for the pinned 15x15 baseline.

This is deliberately an adapter around the original repository rather than a
fork of its training loop.  It checkpoints only at durable transaction
boundaries: a completed self-play game, a completed epoch, and a completed
Arena game.  It never modifies the source checkpoint passed at creation.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import os
import pickle
import shutil
import sys
import tempfile
import time
from collections import deque
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from training.evaluate_arena import (
    restore_numpy_rng_state,
    serialize_numpy_rng_state,
    sha256_file,
    verify_source_commit,
    write_json,
)

RUN_STATE_VERSION = 1
TRAINING_STATE_VERSION = 1
PHASES = {"selfplay", "training", "arena", "complete"}


class TrainingRunError(RuntimeError):
    """A durable local training run cannot safely continue."""


class DotDict(dict[str, Any]):
    """Small compatibility object for the upstream attribute-style args."""

    def __getattr__(self, key: str) -> Any:
        try:
            return self[key]
        except KeyError as exc:
            raise AttributeError(key) from exc


def emit(event: str, **fields: Any) -> None:
    print(json.dumps({"event": event, **fields}, sort_keys=True), flush=True)


def canonical_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def sha256_value(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def atomic_write_bytes(path: Path, value: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False
        ) as output:
            output.write(value)
            output.flush()
            os.fsync(output.fileno())
            temporary = Path(output.name)
        temporary.replace(path)
        fsync_parent(path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def atomic_copy(source: Path, destination: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(f"checkpoint not found: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.tmp")
    try:
        shutil.copyfile(source, temporary)
        with temporary.open("rb") as output:
            os.fsync(output.fileno())
        temporary.replace(destination)
        fsync_parent(destination)
    finally:
        if temporary.exists():
            temporary.unlink()


def atomic_pickle(path: Path, value: Any) -> None:
    atomic_write_bytes(path, pickle.dumps(value, protocol=pickle.HIGHEST_PROTOCOL))


def atomic_torch_save(torch: Any, path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        torch.save(value, temporary)
        with temporary.open("rb") as output:
            os.fsync(output.fileno())
        temporary.replace(path)
        fsync_parent(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def load_yaml(path: Path) -> dict[str, Any]:
    try:
        yaml = importlib.import_module("yaml")
    except ModuleNotFoundError as exc:
        raise TrainingRunError("PyYAML is required; install training/server-requirements.txt") from exc
    with path.open(encoding="utf-8") as source:
        value = yaml.safe_load(source)
    if not isinstance(value, dict):
        raise TrainingRunError("training config root must be a mapping")
    return value


def fsync_parent(path: Path) -> None:
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


def resolve_profile(config: dict[str, Any], profile: str) -> dict[str, Any]:
    baseline = config.get("baseline")
    profiles = config.get("profiles")
    source = config.get("source")
    if not isinstance(baseline, dict) or not isinstance(profiles, dict) or not isinstance(source, dict):
        raise TrainingRunError("training config must contain source, baseline, and profiles mappings")
    settings = profiles.get(profile)
    if not isinstance(settings, dict):
        raise TrainingRunError(f"profile not found: {profile}")
    if baseline.get("board_size") != 15 or baseline.get("num_channels") != 64:
        raise TrainingRunError("local production training only supports the fixed 15x15 / 64-channel contract")
    required = (
        "epochs", "batch_size", "num_iterations", "num_episodes", "max_queue_length",
        "num_iters_history", "arena_compare", "temp_threshold", "num_sims",
    )
    for key in required:
        if not isinstance(settings.get(key), int) or settings[key] <= 0:
            raise TrainingRunError(f"profiles.{profile}.{key} must be a positive integer")
    threshold = settings.get("update_threshold", 0.55)
    if not isinstance(threshold, (float, int)) or not 0 <= float(threshold) <= 1:
        raise TrainingRunError(f"profiles.{profile}.update_threshold must be in [0, 1]")
    return {
        "profile": profile,
        "source_commit": source.get("commit"),
        "board_size": baseline["board_size"],
        "num_channels": baseline["num_channels"],
        "dropout": baseline["dropout"],
        "min_lr": baseline["min_lr"],
        "max_lr": baseline["max_lr"],
        "grad_clip": baseline["grad_clip"],
        "cpuct": baseline["cpuct"],
        "epochs": settings["epochs"],
        "batch_size": settings["batch_size"],
        "num_iterations": settings["num_iterations"],
        "num_episodes": settings["num_episodes"],
        "max_queue_length": settings["max_queue_length"],
        "num_iters_history": settings["num_iters_history"],
        "update_threshold": float(threshold),
        "arena_compare": settings["arena_compare"],
        "temp_threshold": settings["temp_threshold"],
        "num_sims": settings["num_sims"],
    }


def build_identity(resolved: dict[str, Any], source_dir: Path, input_checkpoint: Path, seed: int) -> dict[str, Any]:
    if not isinstance(resolved.get("source_commit"), str) or not resolved["source_commit"]:
        raise TrainingRunError("source.commit must be a non-empty string")
    if not input_checkpoint.is_file():
        raise FileNotFoundError(f"input checkpoint not found: {input_checkpoint}")
    return {
        "source_dir": str(source_dir.resolve()),
        "source_commit": resolved["source_commit"],
        "resolved_config_sha256": sha256_value(resolved),
        "input_checkpoint_sha256": sha256_file(input_checkpoint),
        "board_size": resolved["board_size"],
        "num_channels": resolved["num_channels"],
        "seed": seed,
    }


def new_run_state(identity: dict[str, Any], resolved: dict[str, Any]) -> dict[str, Any]:
    return {
        "version": RUN_STATE_VERSION,
        "identity": identity,
        "phase": "selfplay",
        "iteration": 1,
        "next_episode": 0,
        "completed_epochs": 0,
        "next_arena_game": 0,
        "candidate_wins": 0,
        "baseline_wins": 0,
        "draws": 0,
        "numpy_rng_state": None,
        "current_checkpoint_sha256": identity["input_checkpoint_sha256"],
        "candidate_checkpoint_sha256": None,
        "last_device": None,
        "last_epoch_metrics": None,
        "iteration_results": [],
        "resolved_profile": resolved["profile"],
    }


def validate_run_state(state: dict[str, Any], identity: dict[str, Any], resolved: dict[str, Any]) -> None:
    if state.get("version") != RUN_STATE_VERSION:
        raise TrainingRunError("run state version is unsupported")
    if state.get("identity") != identity:
        raise TrainingRunError("run inputs do not match this persisted training run")
    phase = state.get("phase")
    if phase not in PHASES:
        raise TrainingRunError("run state phase is invalid")
    try:
        iteration = int(state["iteration"])
        next_episode = int(state["next_episode"])
        completed_epochs = int(state["completed_epochs"])
        next_arena_game = int(state["next_arena_game"])
        candidate_wins = int(state["candidate_wins"])
        baseline_wins = int(state["baseline_wins"])
        draws = int(state["draws"])
    except (KeyError, TypeError, ValueError) as exc:
        raise TrainingRunError(f"run state counters are invalid: {exc}") from exc
    if not 1 <= iteration <= resolved["num_iterations"] + 1:
        raise TrainingRunError("run state iteration is out of range")
    if not 0 <= next_episode <= resolved["num_episodes"]:
        raise TrainingRunError("run state next_episode is out of range")
    if not 0 <= completed_epochs <= resolved["epochs"]:
        raise TrainingRunError("run state completed_epochs is out of range")
    if not 0 <= next_arena_game <= resolved["arena_compare"]:
        raise TrainingRunError("run state next_arena_game is out of range")
    if min(candidate_wins, baseline_wins, draws) < 0:
        raise TrainingRunError("run state Arena scores cannot be negative")
    if candidate_wins + baseline_wins + draws != next_arena_game:
        raise TrainingRunError("run state Arena scores do not match completed games")
    if not isinstance(state.get("iteration_results"), list):
        raise TrainingRunError("run state iteration_results must be a list")
    if state.get("last_epoch_metrics") is not None and not isinstance(state["last_epoch_metrics"], dict):
        raise TrainingRunError("run state last_epoch_metrics must be a mapping or null")
    current_hash = state.get("current_checkpoint_sha256")
    if not isinstance(current_hash, str) or len(current_hash) != 64:
        raise TrainingRunError("run state current checkpoint hash is invalid")
    candidate_hash = state.get("candidate_checkpoint_sha256")
    if candidate_hash is not None and (not isinstance(candidate_hash, str) or len(candidate_hash) != 64):
        raise TrainingRunError("run state candidate checkpoint hash is invalid")
    if phase == "complete" and iteration != resolved["num_iterations"] + 1:
        raise TrainingRunError("complete run state must point past the final iteration")


def state_path(run_root: Path) -> Path:
    return run_root / "run-state.json"


def read_state(run_root: Path, identity: dict[str, Any], resolved: dict[str, Any]) -> dict[str, Any]:
    path = state_path(run_root)
    try:
        state = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise TrainingRunError(f"no training run exists at {run_root}") from exc
    except json.JSONDecodeError as exc:
        raise TrainingRunError(f"run state is not valid JSON: {exc}") from exc
    if not isinstance(state, dict):
        raise TrainingRunError("run state must be a JSON object")
    validate_run_state(state, identity, resolved)
    return state


def write_state(run_root: Path, state: dict[str, Any]) -> None:
    write_json(state_path(run_root), state)


def create_or_load_state(
    run_root: Path,
    identity: dict[str, Any],
    resolved: dict[str, Any],
    input_checkpoint: Path,
) -> dict[str, Any]:
    path = state_path(run_root)
    if path.exists():
        return read_state(run_root, identity, resolved)
    run_root.mkdir(parents=True, exist_ok=True)
    resolved_path = run_root / "resolved-config.json"
    current = run_root / "checkpoints" / "current.pth.tar"
    if resolved_path.exists():
        try:
            stored = json.loads(resolved_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise TrainingRunError(f"resolved config is not valid JSON: {exc}") from exc
        if stored != resolved:
            raise TrainingRunError("run root has a different resolved config but no valid run state")
        if current.exists() and sha256_file(current) != sha256_file(input_checkpoint):
            raise TrainingRunError("run root has an unexpected current checkpoint but no valid run state")
    else:
        write_json(resolved_path, resolved)
    if not current.exists():
        atomic_copy(input_checkpoint, current)
    state = new_run_state(identity, resolved)
    write_state(run_root, state)
    return state


def load_upstream(source_dir: Path) -> tuple[Any, Any]:
    if not (source_dir / "alphazero.py").is_file() or not (source_dir / "game.py").is_file():
        raise FileNotFoundError(f"upstream AlphaZero source is incomplete: {source_dir}")
    source_text = str(source_dir.resolve())
    sys.path.insert(0, source_text)
    try:
        importlib.invalidate_caches()
        return importlib.import_module("alphazero"), importlib.import_module("game")
    finally:
        sys.path.remove(source_text)


def choose_runtime_device(torch: Any, requested: str) -> tuple[Any, dict[str, bool]]:
    if requested not in {"auto", "cpu", "mps"}:
        raise ValueError("device must be one of: auto, cpu, mps")
    mps_backend = getattr(torch.backends, "mps", None)
    mps_available = bool(mps_backend and mps_backend.is_available())
    if requested == "mps" and not mps_available:
        raise TrainingRunError("MPS was requested but is unavailable in this Python process")
    selected = "mps" if requested == "mps" or (requested == "auto" and mps_available) else "cpu"
    return torch.device(selected), {"mps_available": mps_available}


def build_args(resolved: dict[str, Any], run_root: Path) -> DotDict:
    return DotDict(
        epochs=resolved["epochs"],
        batch_size=resolved["batch_size"],
        numIters=resolved["num_iterations"],
        numEps=resolved["num_episodes"],
        maxlenOfQueue=resolved["max_queue_length"],
        numItersForTrainExamplesHistory=resolved["num_iters_history"],
        updateThreshold=resolved["update_threshold"],
        arenaCompare=resolved["arena_compare"],
        tempThreshold=resolved["temp_threshold"],
        num_channels=resolved["num_channels"],
        dropout=resolved["dropout"],
        min_lr=resolved["min_lr"],
        max_lr=resolved["max_lr"],
        grad_clip=resolved["grad_clip"],
        numMCTSSims=resolved["num_sims"],
        cpuct=resolved["cpuct"],
        board_size=resolved["board_size"],
        cuda=False,
        checkpoint=str(run_root / "checkpoints"),
        wandb=False,
    )


def cpu_copy(value: Any) -> Any:
    if hasattr(value, "detach") and hasattr(value, "cpu"):
        return value.detach().cpu()
    if isinstance(value, dict):
        return {key: cpu_copy(item) for key, item in value.items()}
    if isinstance(value, list):
        return [cpu_copy(item) for item in value]
    if isinstance(value, tuple):
        return tuple(cpu_copy(item) for item in value)
    return value


def move_optimizer_state(optimizer: Any, device: Any) -> None:
    for value in optimizer.state.values():
        for key, item in list(value.items()):
            if hasattr(item, "to"):
                value[key] = item.to(device)


class DeviceNetwork:
    """The upstream MCTS interface, with device-aware inference."""

    def __init__(self, torch: Any, model: Any, board_size: int, device: Any) -> None:
        self.torch = torch
        self.model = model
        self.board_size = board_size
        self.device = device

    def predict(self, board: Any) -> tuple[Any, Any]:
        tensor = self.torch.as_tensor(board, dtype=self.torch.float32, device=self.device)
        tensor = tensor.view(1, self.board_size, self.board_size)
        self.model.eval()
        with self.torch.no_grad():
            policy, value = self.model(tensor)
        return self.torch.exp(policy).detach().cpu().numpy()[0], value.detach().cpu().numpy()[0]


def load_weights(torch: Any, model: Any, path: Path) -> None:
    try:
        try:
            payload = torch.load(path, map_location="cpu", weights_only=True)
        except TypeError:
            payload = torch.load(path, map_location="cpu")
    except Exception as exc:
        raise TrainingRunError(f"cannot load checkpoint {path}: {exc}") from exc
    weights = payload.get("state_dict") if isinstance(payload, dict) else None
    if not isinstance(weights, dict):
        raise TrainingRunError(f"checkpoint {path} does not contain state_dict")
    try:
        model.load_state_dict(weights, strict=True)
    except Exception as exc:
        raise TrainingRunError(f"checkpoint {path} is incompatible with the fixed model: {exc}") from exc


def save_model(torch: Any, model: Any, path: Path) -> None:
    atomic_torch_save(torch, path, {"state_dict": cpu_copy(model.state_dict())})


def build_model(torch: Any, upstream: Any, game: Any, args: DotDict, device: Any, checkpoint: Path) -> Any:
    wrapper = upstream.NNetWrapper(game, args)
    model = wrapper.nnet.to(device)
    load_weights(torch, model, checkpoint)
    model.eval()
    return model


def episode_path(run_root: Path, iteration: int, episode: int) -> Path:
    return run_root / "replay" / f"iteration-{iteration:03d}" / f"episode-{episode:04d}.pkl"


def training_path(run_root: Path, iteration: int) -> Path:
    return run_root / "checkpoints" / f"training-state-{iteration:03d}.pth.tar"


def legacy_training_path(run_root: Path) -> Path:
    """The pre-iteration-scoped path, retained only for safe migration."""
    return run_root / "checkpoints" / "training-state.pth.tar"


def candidate_path(run_root: Path, iteration: int) -> Path:
    return run_root / "checkpoints" / f"candidate-{iteration:03d}.pth.tar"


def arena_path(run_root: Path, iteration: int) -> Path:
    return run_root / "arena" / f"iteration-{iteration:03d}-state.json"


def execute_episode(upstream: Any, game: Any, network: DeviceNetwork, args: DotDict) -> list[tuple[Any, Any, float]]:
    mcts = upstream.MCTS(game, network, args)
    examples: list[list[Any]] = []
    board = game.getInitBoard()
    player = 1
    step = 0
    while True:
        step += 1
        canonical = game.getCanonicalForm(board, player)
        policy = mcts.getActionProb(canonical, temp=int(step < args.tempThreshold))
        for symmetric_board, symmetric_policy in game.getSymmetries(canonical, policy):
            examples.append([symmetric_board, player, symmetric_policy, None])
        action = upstream.np.random.choice(len(policy), p=policy)
        board, player = game.getNextState(board, player, action)
        result = game.getGameEnded(board, player)
        if result is not None:
            return [
                (item[0], item[2], float(result * (1 if player == item[1] else -1)))
                for item in examples
            ]


def load_training_examples(run_root: Path, iteration: int, resolved: dict[str, Any]) -> list[Any]:
    first = max(1, iteration - resolved["num_iters_history"] + 1)
    examples: list[Any] = []
    for history_iteration in range(first, iteration + 1):
        iteration_examples: deque[Any] = deque(maxlen=resolved["max_queue_length"])
        for episode in range(resolved["num_episodes"]):
            path = episode_path(run_root, history_iteration, episode)
            try:
                with path.open("rb") as source:
                    stored = pickle.load(source)
            except FileNotFoundError as exc:
                raise TrainingRunError(f"missing committed replay episode: {path}") from exc
            except (pickle.PickleError, EOFError, ValueError, TypeError) as exc:
                raise TrainingRunError(f"corrupt replay episode {path}: {exc}") from exc
            if not isinstance(stored, dict) or not isinstance(stored.get("examples"), list):
                raise TrainingRunError(f"replay episode must contain a committed examples payload: {path}")
            iteration_examples.extend(stored["examples"])
        examples.extend(iteration_examples)
    if len(examples) < resolved["batch_size"]:
        raise TrainingRunError(
            f"only {len(examples)} examples are available; need at least one {resolved['batch_size']}-item batch"
        )
    return examples


def create_training_state(torch: Any, model: Any, optimizer: Any, iteration: int, current_step: int) -> dict[str, Any]:
    return {
        "version": TRAINING_STATE_VERSION,
        "iteration": iteration,
        "completed_epochs": 0,
        "current_step": current_step,
        "model_state": cpu_copy(model.state_dict()),
        "optimizer_state": cpu_copy(optimizer.state_dict()),
    }


def load_training_state(torch: Any, model: Any, optimizer: Any, path: Path, iteration: int, device: Any) -> tuple[int, int]:
    try:
        payload = torch.load(path, map_location="cpu", weights_only=False)
    except TypeError:
        payload = torch.load(path, map_location="cpu")
    except Exception as exc:
        raise TrainingRunError(f"cannot load training state {path}: {exc}") from exc
    if not isinstance(payload, dict) or payload.get("version") != TRAINING_STATE_VERSION:
        raise TrainingRunError("training state version is unsupported")
    if payload.get("iteration") != iteration:
        raise TrainingRunError("training state belongs to a different iteration")
    completed_epochs = payload.get("completed_epochs")
    current_step = payload.get("current_step")
    if not isinstance(completed_epochs, int) or not isinstance(current_step, int) or completed_epochs < 0 or current_step < 0:
        raise TrainingRunError("training state counters are invalid")
    try:
        model.load_state_dict(payload["model_state"], strict=True)
        optimizer.load_state_dict(payload["optimizer_state"])
        move_optimizer_state(optimizer, device)
    except (KeyError, TypeError, ValueError, RuntimeError) as exc:
        raise TrainingRunError(f"training state is incompatible: {exc}") from exc
    return completed_epochs, current_step


def migrate_legacy_training_state(torch: Any, run_root: Path, iteration: int, destination: Path) -> bool:
    """Copy a compatible first-generation state without deleting its source."""
    legacy = legacy_training_path(run_root)
    if destination.exists() or not legacy.exists():
        return False
    try:
        payload = torch.load(legacy, map_location="cpu", weights_only=False)
    except TypeError:
        payload = torch.load(legacy, map_location="cpu")
    except Exception as exc:
        raise TrainingRunError(f"cannot inspect legacy training state {legacy}: {exc}") from exc
    if not isinstance(payload, dict) or payload.get("version") != TRAINING_STATE_VERSION:
        raise TrainingRunError("legacy training state version is unsupported")
    if payload.get("iteration") != iteration:
        return False
    atomic_torch_save(torch, destination, payload)
    emit("training-state-migrated", iteration=iteration, source=str(legacy), destination=str(destination))
    return True


def one_cycle_lr(resolved: dict[str, Any], current_step: int, batches_per_epoch: int) -> float:
    total_steps = resolved["num_iterations"] * resolved["epochs"] * batches_per_epoch
    if current_step >= total_steps:
        return float(resolved["min_lr"])
    half_cycle = max(1, total_steps // 2)
    if current_step <= half_cycle:
        phase = current_step / half_cycle
        return float(resolved["min_lr"] + (resolved["max_lr"] - resolved["min_lr"]) * phase)
    phase = (current_step - half_cycle) / half_cycle
    return float(resolved["max_lr"] - (resolved["max_lr"] - resolved["min_lr"]) * phase)


def train_epoch(
    torch: Any,
    model: Any,
    optimizer: Any,
    examples: list[Any],
    resolved: dict[str, Any],
    iteration: int,
    epoch: int,
    seed: int,
    current_step: int,
    device: Any,
    progress_every_batches: int,
) -> tuple[int, dict[str, float]]:
    import numpy as np

    batches = len(examples) // resolved["batch_size"]
    if batches == 0:
        raise TrainingRunError("no full batches available for training")
    random = np.random.RandomState(seed + iteration * 100_000 + epoch)
    model.train()
    total_policy = total_value = 0.0
    start = time.monotonic()
    for batch_index in range(batches):
        sample_ids = random.randint(len(examples), size=resolved["batch_size"])
        boards, policies, values = zip(*(examples[index] for index in sample_ids))
        board_tensor = torch.as_tensor(np.asarray(boards, dtype=np.float32), device=device)
        policy_tensor = torch.as_tensor(np.asarray(policies, dtype=np.float32), device=device)
        value_tensor = torch.as_tensor(np.asarray(values, dtype=np.float32), device=device)
        learning_rate = one_cycle_lr(resolved, current_step, batches)
        for group in optimizer.param_groups:
            group["lr"] = learning_rate
        policy_output, value_output = model(board_tensor)
        policy_loss = -torch.sum(policy_tensor * policy_output) / board_tensor.size(0)
        value_loss = torch.sum((value_tensor - value_output.view(-1)) ** 2) / board_tensor.size(0)
        loss = policy_loss + value_loss
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), resolved["grad_clip"])
        optimizer.step()
        current_step += 1
        total_policy += float(policy_loss.detach().cpu())
        total_value += float(value_loss.detach().cpu())
        completed_batches = batch_index + 1
        if completed_batches == batches or completed_batches % progress_every_batches == 0:
            emit(
                "training-batch-progress",
                iteration=iteration,
                epoch=epoch,
                completed_batches=completed_batches,
                batches=batches,
                policy_loss=total_policy / completed_batches,
                value_loss=total_value / completed_batches,
                elapsed_seconds=round(time.monotonic() - start, 3),
            )
    elapsed = time.monotonic() - start
    return current_step, {
        "policy_loss": total_policy / batches,
        "value_loss": total_value / batches,
        "elapsed_seconds": elapsed,
        "batches": float(batches),
    }


def iteration_decision(candidate_wins: int, baseline_wins: int, draws: int, threshold: float) -> dict[str, Any]:
    decisive = candidate_wins + baseline_wins
    win_rate = candidate_wins / decisive if decisive else None
    accepted = decisive > 0 and win_rate is not None and win_rate >= threshold
    return {
        "status": "ACCEPTED" if accepted else "REJECTED",
        "candidate_win_rate": win_rate,
        "candidate_wins": candidate_wins,
        "baseline_wins": baseline_wins,
        "draws": draws,
        "threshold": threshold,
        "reason": "upstream update threshold met" if accepted else "upstream update threshold not met",
    }


def record_arena_result(state: dict[str, Any], candidate_result: int, np_module: Any) -> None:
    if candidate_result == 1:
        state["candidate_wins"] += 1
    elif candidate_result == -1:
        state["baseline_wins"] += 1
    elif candidate_result == 0:
        state["draws"] += 1
    else:
        raise TrainingRunError(f"Arena returned invalid game result: {candidate_result}")
    state["next_arena_game"] += 1
    state["numpy_rng_state"] = serialize_numpy_rng_state(np_module)


def write_arena_snapshot(run_root: Path, state: dict[str, Any]) -> None:
    iteration = state["iteration"]
    write_json(
        arena_path(run_root, iteration),
        {
            "iteration": iteration,
            "next_arena_game": state["next_arena_game"],
            "candidate_wins": state["candidate_wins"],
            "baseline_wins": state["baseline_wins"],
            "draws": state["draws"],
            "numpy_rng_state": state["numpy_rng_state"],
        },
    )


def recover_episode_if_needed(run_root: Path, state: dict[str, Any], upstream: Any) -> bool:
    output = episode_path(run_root, state["iteration"], state["next_episode"])
    if not output.exists():
        return False
    try:
        with output.open("rb") as source:
            payload = pickle.load(source)
    except (pickle.PickleError, EOFError, ValueError, TypeError) as exc:
        raise TrainingRunError(f"corrupt pending replay episode {output}: {exc}") from exc
    if not isinstance(payload, dict) or not isinstance(payload.get("examples"), list) or not isinstance(
        payload.get("numpy_rng_state"), dict
    ):
        raise TrainingRunError(f"pending replay episode has invalid transaction payload: {output}")
    state["next_episode"] += 1
    state["numpy_rng_state"] = payload["numpy_rng_state"]
    restore_numpy_rng_state(upstream.np, state["numpy_rng_state"])
    write_state(run_root, state)
    emit("selfplay-recovered", iteration=state["iteration"], completed_episodes=state["next_episode"])
    return True


def recover_arena_if_needed(run_root: Path, state: dict[str, Any], upstream: Any) -> bool:
    path = arena_path(run_root, state["iteration"])
    if not path.exists():
        return False
    try:
        snapshot = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise TrainingRunError(f"Arena snapshot is not valid JSON: {exc}") from exc
    if not isinstance(snapshot, dict) or snapshot.get("iteration") != state["iteration"]:
        raise TrainingRunError("Arena snapshot is incompatible with current iteration")
    completed = snapshot.get("next_arena_game")
    if not isinstance(completed, int):
        raise TrainingRunError("Arena snapshot completed-game counter is invalid")
    if completed <= state["next_arena_game"]:
        return False
    keys = ("candidate_wins", "baseline_wins", "draws")
    if any(not isinstance(snapshot.get(key), int) or snapshot[key] < 0 for key in keys):
        raise TrainingRunError("Arena snapshot scores are invalid")
    if snapshot["candidate_wins"] + snapshot["baseline_wins"] + snapshot["draws"] != completed:
        raise TrainingRunError("Arena snapshot scores do not match completed games")
    if not isinstance(snapshot.get("numpy_rng_state"), dict):
        raise TrainingRunError("Arena snapshot has no recoverable NumPy RNG state")
    state["next_arena_game"] = completed
    for key in keys:
        state[key] = snapshot[key]
    state["numpy_rng_state"] = snapshot["numpy_rng_state"]
    restore_numpy_rng_state(upstream.np, state["numpy_rng_state"])
    write_state(run_root, state)
    emit("arena-recovered", iteration=state["iteration"], completed_games=completed)
    return True


def commit_path(run_root: Path, iteration: int) -> Path:
    return run_root / "commits" / f"iteration-{iteration:03d}.json"


def advance_after_commit(state: dict[str, Any], resolved: dict[str, Any]) -> None:
    state["iteration"] += 1
    state["next_episode"] = 0
    state["completed_epochs"] = 0
    state["next_arena_game"] = 0
    state["candidate_wins"] = 0
    state["baseline_wins"] = 0
    state["draws"] = 0
    state["candidate_checkpoint_sha256"] = None
    state["phase"] = "complete" if state["iteration"] > resolved["num_iterations"] else "selfplay"


def finalize_pending_commit(run_root: Path, state: dict[str, Any], resolved: dict[str, Any]) -> bool:
    active = commit_path(run_root, state["iteration"])
    if not active.exists():
        previous = commit_path(run_root, state["iteration"] - 1)
        if previous.exists():
            previous.unlink()
        return False
    try:
        commit = json.loads(active.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise TrainingRunError(f"iteration commit is not valid JSON: {exc}") from exc
    if not isinstance(commit, dict) or commit.get("iteration") != state["iteration"]:
        raise TrainingRunError("iteration commit does not match current state")
    decision = commit.get("decision")
    if not isinstance(decision, dict) or decision.get("candidate_sha256") != state.get("candidate_checkpoint_sha256"):
        raise TrainingRunError("iteration commit does not match candidate checkpoint")
    candidate = candidate_path(run_root, state["iteration"])
    if not candidate.is_file() or sha256_file(candidate) != decision["candidate_sha256"]:
        raise TrainingRunError("candidate checkpoint changed before iteration commit")
    if decision.get("status") == "ACCEPTED":
        current = run_root / "checkpoints" / "current.pth.tar"
        atomic_copy(candidate, current)
        atomic_copy(candidate, run_root / "checkpoints" / "accepted" / f"iteration-{state['iteration']:03d}.pth.tar")
        state["current_checkpoint_sha256"] = decision["candidate_sha256"]
    state["iteration_results"].append(decision)
    advance_after_commit(state, resolved)
    write_state(run_root, state)
    write_evidence(run_root, state, resolved)
    active.unlink()
    emit("iteration-complete", **decision)
    return True


def write_evidence(run_root: Path, state: dict[str, Any], resolved: dict[str, Any]) -> None:
    write_json(
        run_root / "evidence.json",
        {
            "promotion_authority": False,
            "reason": "local MPS/CPU training does not replace the CUDA production gate",
            "profile": resolved["profile"],
            "completed_iterations": state["iteration_results"],
            "current_phase": state["phase"],
            "current_iteration": state["iteration"],
            "last_device": state["last_device"],
        },
    )


def run_training(
    source_dir: Path,
    resolved: dict[str, Any],
    run_root: Path,
    state: dict[str, Any],
    torch: Any,
    device: Any,
    progress_every_batches: int,
) -> None:
    upstream, game_module = load_upstream(source_dir)
    args = build_args(resolved, run_root)
    game = game_module.GomokuGame(resolved["board_size"])
    if state["numpy_rng_state"] is None:
        upstream.np.random.seed(state["identity"]["seed"])
        state["numpy_rng_state"] = serialize_numpy_rng_state(upstream.np)
        write_state(run_root, state)
    else:
        restore_numpy_rng_state(upstream.np, state["numpy_rng_state"])

    while state["phase"] != "complete":
        iteration = state["iteration"]
        if finalize_pending_commit(run_root, state, resolved):
            continue
        current = run_root / "checkpoints" / "current.pth.tar"
        if not current.is_file() or sha256_file(current) != state["current_checkpoint_sha256"]:
            raise TrainingRunError("current checkpoint is missing or changed outside this run")
        if state["phase"] == "selfplay":
            model = build_model(torch, upstream, game, args, device, current)
            network = DeviceNetwork(torch, model, resolved["board_size"], device)
            while state["next_episode"] < resolved["num_episodes"]:
                episode = state["next_episode"]
                if recover_episode_if_needed(run_root, state, upstream):
                    continue
                started = time.monotonic()
                examples = execute_episode(upstream, game, network, args)
                output = episode_path(run_root, iteration, episode)
                after_game_rng = serialize_numpy_rng_state(upstream.np)
                atomic_pickle(output, {"examples": examples, "numpy_rng_state": after_game_rng})
                state["next_episode"] += 1
                state["numpy_rng_state"] = after_game_rng
                write_state(run_root, state)
                emit(
                    "selfplay-progress",
                    iteration=iteration,
                    completed_episodes=state["next_episode"],
                    episodes=resolved["num_episodes"],
                    examples=len(examples),
                    elapsed_seconds=round(time.monotonic() - started, 3),
                )
            state["phase"] = "training"
            state["completed_epochs"] = 0
            write_state(run_root, state)
            emit("phase-complete", phase="selfplay", iteration=iteration)
            continue

        if state["phase"] == "training":
            examples = load_training_examples(run_root, iteration, resolved)
            model = build_model(torch, upstream, game, args, device, current)
            optimizer = torch.optim.Adam(model.parameters(), lr=resolved["max_lr"])
            state_file = training_path(run_root, iteration)
            migrate_legacy_training_state(torch, run_root, iteration, state_file)
            if state_file.exists():
                completed_epochs, current_step = load_training_state(
                    torch, model, optimizer, state_file, iteration, device
                )
                if completed_epochs < state["completed_epochs"]:
                    raise TrainingRunError("training checkpoint predates the durable run state")
                if completed_epochs > state["completed_epochs"]:
                    state["completed_epochs"] = completed_epochs
                    write_state(run_root, state)
                    emit("training-recovered", iteration=iteration, completed_epochs=completed_epochs)
            else:
                completed_epochs, current_step = state["completed_epochs"], 0
                if completed_epochs != 0:
                    raise TrainingRunError("training checkpoint is missing after a completed epoch")
                atomic_torch_save(
                    torch, state_file, create_training_state(torch, model, optimizer, iteration, current_step)
                )
            while completed_epochs < resolved["epochs"]:
                epoch = completed_epochs + 1
                current_step, metrics = train_epoch(
                    torch, model, optimizer, examples, resolved, iteration, epoch,
                    state["identity"]["seed"], current_step, device, progress_every_batches,
                )
                completed_epochs = epoch
                checkpoint = create_training_state(torch, model, optimizer, iteration, current_step)
                checkpoint["completed_epochs"] = completed_epochs
                checkpoint["last_epoch_metrics"] = metrics
                atomic_torch_save(torch, state_file, checkpoint)
                state["completed_epochs"] = completed_epochs
                state["last_epoch_metrics"] = {
                    "iteration": iteration,
                    "epoch": completed_epochs,
                    "policy_loss": metrics["policy_loss"],
                    "value_loss": metrics["value_loss"],
                    "elapsed_seconds": metrics["elapsed_seconds"],
                }
                write_state(run_root, state)
                emit(
                    "training-epoch-complete",
                    iteration=iteration,
                    epoch=completed_epochs,
                    epochs=resolved["epochs"],
                    policy_loss=metrics["policy_loss"],
                    value_loss=metrics["value_loss"],
                    elapsed_seconds=round(metrics["elapsed_seconds"], 3),
                )
            candidate = candidate_path(run_root, iteration)
            save_model(torch, model, candidate)
            state["phase"] = "arena"
            state["candidate_checkpoint_sha256"] = sha256_file(candidate)
            state["next_arena_game"] = 0
            state["candidate_wins"] = 0
            state["baseline_wins"] = 0
            state["draws"] = 0
            write_state(run_root, state)
            write_arena_snapshot(run_root, state)
            emit("phase-complete", phase="training", iteration=iteration, candidate_sha256=sha256_file(candidate))
            continue

        if state["phase"] == "arena":
            candidate = candidate_path(run_root, iteration)
            if not candidate.is_file():
                raise TrainingRunError(f"candidate checkpoint is missing: {candidate}")
            if sha256_file(candidate) != state["candidate_checkpoint_sha256"]:
                raise TrainingRunError("candidate checkpoint changed outside this run")
            recover_arena_if_needed(run_root, state, upstream)
            if finalize_pending_commit(run_root, state, resolved):
                continue
            candidate_model = build_model(torch, upstream, game, args, device, candidate)
            baseline_model = build_model(torch, upstream, game, args, device, current)
            candidate_network = DeviceNetwork(torch, candidate_model, resolved["board_size"], device)
            baseline_network = DeviceNetwork(torch, baseline_model, resolved["board_size"], device)
            first_half = resolved["arena_compare"] // 2
            while state["next_arena_game"] < resolved["arena_compare"]:
                game_index = state["next_arena_game"]
                candidate_mcts = upstream.MCTS(game, candidate_network, args)
                baseline_mcts = upstream.MCTS(game, baseline_network, args)
                candidate_first = game_index < first_half

                def candidate_player(board: Any) -> int:
                    return int(upstream.np.argmax(candidate_mcts.getActionProb(board, temp=0)))

                def baseline_player(board: Any) -> int:
                    return int(upstream.np.argmax(baseline_mcts.getActionProb(board, temp=0)))

                arena = game_module.Arena(
                    candidate_player if candidate_first else baseline_player,
                    baseline_player if candidate_first else candidate_player,
                    game,
                )
                started = time.monotonic()
                player_one_result = arena.playGame()
                record_arena_result(
                    state, player_one_result if candidate_first else -player_one_result, upstream.np
                )
                write_arena_snapshot(run_root, state)
                write_state(run_root, state)
                emit(
                    "arena-progress",
                    iteration=iteration,
                    completed_games=state["next_arena_game"],
                    games=resolved["arena_compare"],
                    candidate_wins=state["candidate_wins"],
                    baseline_wins=state["baseline_wins"],
                    draws=state["draws"],
                    elapsed_seconds=round(time.monotonic() - started, 3),
                )
            decision = iteration_decision(
                state["candidate_wins"], state["baseline_wins"], state["draws"], resolved["update_threshold"]
            )
            decision["iteration"] = iteration
            decision["candidate_sha256"] = state["candidate_checkpoint_sha256"]
            write_json(commit_path(run_root, iteration), {"iteration": iteration, "decision": decision})
            finalize_pending_commit(run_root, state, resolved)
            continue

        raise TrainingRunError(f"unhandled phase: {state['phase']}")

    write_evidence(run_root, state, resolved)
    emit("run-complete", iterations=len(state["iteration_results"]), run_root=str(run_root))


def status_payload(run_root: Path, state: dict[str, Any], resolved: dict[str, Any]) -> dict[str, Any]:
    latest = state["iteration_results"][-1] if state["iteration_results"] else None
    return {
        "event": "training-status",
        "run_root": str(run_root),
        "profile": resolved["profile"],
        "phase": state["phase"],
        "iteration": state["iteration"],
        "completed_episodes": state["next_episode"],
        "episodes": resolved["num_episodes"],
        "completed_epochs": state["completed_epochs"],
        "epochs": resolved["epochs"],
        "completed_arena_games": state["next_arena_game"],
        "arena_games": resolved["arena_compare"],
        "last_device": state["last_device"],
        "latest_result": latest,
        "latest_epoch": state.get("last_epoch_metrics"),
        "promotion_authority": False,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--input-checkpoint", type=Path, required=True)
    parser.add_argument("--run-root", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("server-baseline.yaml"))
    parser.add_argument("--profile", default="production")
    parser.add_argument("--device", choices=("auto", "cpu", "mps"), default="auto")
    parser.add_argument("--seed", type=int, default=20260828)
    parser.add_argument("--progress-every-batches", type=int, default=50)
    parser.add_argument("--status", action="store_true", help="print current durable training result and exit")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        if args.progress_every_batches <= 0:
            raise ValueError("progress-every-batches must be positive")
        config = load_yaml(args.config)
        resolved = resolve_profile(config, args.profile)
        verify_source_commit(args.source_dir, resolved["source_commit"])
        identity = build_identity(resolved, args.source_dir, args.input_checkpoint, args.seed)
        if args.status:
            state = read_state(args.run_root, identity, resolved)
            emit(**status_payload(args.run_root, state, resolved))
            return 0
        torch = importlib.import_module("torch")
        device, availability = choose_runtime_device(torch, args.device)
        state = create_or_load_state(args.run_root, identity, resolved, args.input_checkpoint)
        state["last_device"] = device.type
        write_state(args.run_root, state)
        emit(
            "run-start",
            device=device.type,
            requested_device=args.device,
            mps_available=availability["mps_available"],
            profile=resolved["profile"],
            phase=state["phase"],
            iteration=state["iteration"],
            previous_result=state["iteration_results"][-1] if state["iteration_results"] else None,
        )
        run_training(args.source_dir, resolved, args.run_root, state, torch, device, args.progress_every_batches)
        return 0
    except (FileNotFoundError, ModuleNotFoundError, TrainingRunError, ValueError) as exc:
        print(f"local training failed: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        emit("run-interrupted", message="last committed boundary is durable; rerun the same command to resume")
        return 130


if __name__ == "__main__":
    raise SystemExit(main())

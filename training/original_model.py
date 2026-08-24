"""Lazy adapter for the original Nagi-ovo PyTorch network.

Importing this module never imports torch. That keeps contract tests runnable
on a clean M4 checkout before the optional ML environment is installed.
"""

from __future__ import annotations

import importlib
from pathlib import Path
from typing import Any


class DependencyError(RuntimeError):
    pass


class CheckpointError(RuntimeError):
    pass


def require_torch() -> Any:
    try:
        return importlib.import_module("torch")
    except ModuleNotFoundError as exc:
        raise DependencyError(
            "PyTorch is required for checkpoint loading/export. Install the training extras "
            "with `python3 -m pip install -r training/requirements.txt` in a virtualenv."
        ) from exc


def choose_device(torch: Any, requested: str = "auto") -> Any:
    if requested not in {"auto", "cpu", "mps", "cuda"}:
        raise ValueError("device must be one of: auto, cpu, mps, cuda")
    mps = bool(getattr(torch.backends, "mps", None) and torch.backends.mps.is_available())
    cuda = bool(torch.cuda.is_available())
    selected = requested
    if requested == "auto":
        selected = "mps" if mps else "cuda" if cuda else "cpu"
    if selected == "mps" and not mps:
        raise RuntimeError("MPS was requested but torch.backends.mps.is_available() is false")
    if selected == "cuda" and not cuda:
        raise RuntimeError("CUDA was requested but torch.cuda.is_available() is false")
    return torch.device(selected)


def create_original_model(torch: Any, board_size: int = 15, num_channels: int = 512) -> Any:
    if board_size != 15:
        raise ValueError("the Android model contract only supports board_size=15")
    if num_channels <= 0:
        raise ValueError("num_channels must be positive")
    nn = torch.nn
    functional = torch.nn.functional

    class GomokuNNet(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.board_x = board_size
            self.board_y = board_size
            self.action_size = board_size * board_size
            self.conv1 = nn.Conv2d(1, num_channels, 3, stride=1, padding=1)
            self.conv2 = nn.Conv2d(num_channels, num_channels, 3, stride=1, padding=1)
            self.conv3 = nn.Conv2d(num_channels, num_channels, 3, stride=1)
            self.conv4 = nn.Conv2d(num_channels, num_channels, 3, stride=1)
            self.bn1 = nn.BatchNorm2d(num_channels)
            self.bn2 = nn.BatchNorm2d(num_channels)
            self.bn3 = nn.BatchNorm2d(num_channels)
            self.bn4 = nn.BatchNorm2d(num_channels)
            self.fc1 = nn.Linear(num_channels * (board_size - 4) * (board_size - 4), 1024)
            self.fc_bn1 = nn.BatchNorm1d(1024)
            self.fc2 = nn.Linear(1024, 512)
            self.fc_bn2 = nn.BatchNorm1d(512)
            self.fc3 = nn.Linear(512, self.action_size)
            self.fc4 = nn.Linear(512, 1)

        def forward(self, board: Any) -> tuple[Any, Any]:
            value = board.view(-1, 1, self.board_x, self.board_y)
            value = functional.relu(self.bn1(self.conv1(value)))
            value = functional.relu(self.bn2(self.conv2(value)))
            value = functional.relu(self.bn3(self.conv3(value)))
            value = functional.relu(self.bn4(self.conv4(value)))
            value = value.view(-1, num_channels * (self.board_x - 4) * (self.board_y - 4))
            value = functional.dropout(functional.relu(self.fc_bn1(self.fc1(value))), p=0.1, training=self.training)
            value = functional.dropout(functional.relu(self.fc_bn2(self.fc2(value))), p=0.1, training=self.training)
            return functional.log_softmax(self.fc3(value), dim=1), torch.tanh(self.fc4(value))

    return GomokuNNet()


def load_checkpoint(
    checkpoint_path: Path,
    *,
    board_size: int = 15,
    num_channels: int = 512,
    requested_device: str = "auto",
) -> tuple[Any, Any, Any]:
    """Return ``(torch, eval_model, selected_device)`` with actionable errors."""
    if not checkpoint_path.exists():
        raise CheckpointError(
            f"checkpoint not found: {checkpoint_path}. Provide a 15x15 `best.pth.tar`; no model asset is bundled."
        )
    if not checkpoint_path.is_file():
        raise CheckpointError(f"checkpoint path is not a file: {checkpoint_path}")
    torch = require_torch()
    device = choose_device(torch, requested_device)
    model = create_original_model(torch, board_size, num_channels)
    try:
        try:
            checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=True)
        except TypeError:
            checkpoint = torch.load(checkpoint_path, map_location=device)
        state_dict = checkpoint.get("state_dict") if isinstance(checkpoint, dict) else None
        if state_dict is None and isinstance(checkpoint, dict):
            state_dict = checkpoint
        if not isinstance(state_dict, dict):
            raise CheckpointError("checkpoint must contain a `state_dict` mapping")
        model.load_state_dict(state_dict, strict=True)
    except CheckpointError:
        raise
    except Exception as exc:
        raise CheckpointError(
            "checkpoint is incompatible with the original 15x15 network. "
            f"Check board size and --num-channels (currently {num_channels}); loader said: {exc}"
        ) from exc
    model.to(device)
    model.eval()
    return torch, model, device

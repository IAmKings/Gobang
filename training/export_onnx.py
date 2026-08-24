#!/usr/bin/env python3
"""Export the original PyTorch model to the Android policy/value contract."""

from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from training.contract import ACTION_SIZE, BOARD_SIZE, build_manifest, write_json
from training.original_model import CheckpointError, DependencyError, load_checkpoint


def _export(torch, model, output: Path, opset: int) -> None:
    class ContractWrapper(torch.nn.Module):
        def __init__(self, wrapped):
            super().__init__()
            self.wrapped = wrapped

        def forward(self, board):
            log_policy, value = self.wrapped(board)
            return torch.exp(log_policy), value

    wrapper = ContractWrapper(model).eval().to("cpu")
    dummy = torch.zeros((1, 1, BOARD_SIZE, BOARD_SIZE), dtype=torch.float32)
    output.parent.mkdir(parents=True, exist_ok=True)
    kwargs = dict(
        input_names=["canonical_board"],
        output_names=["policy", "value"],
        opset_version=opset,
        do_constant_folding=True,
        dynamic_axes=None,
    )
    try:
        torch.onnx.export(wrapper, dummy, str(output), dynamo=False, **kwargs)
    except TypeError:
        # Older PyTorch versions do not have the dynamo argument.
        torch.onnx.export(wrapper, dummy, str(output), **kwargs)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--model-version", default="mvp-original-15x15")
    parser.add_argument("--num-channels", type=int, default=512)
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument("--device", choices=("auto", "cpu", "mps", "cuda"), default="auto")
    parser.add_argument("--seed", type=int, default=20260825)
    args = parser.parse_args(argv)
    manifest_path = args.manifest or args.output.with_name("model_manifest.json")
    try:
        random.seed(args.seed)
        torch, model, device = load_checkpoint(
            args.checkpoint,
            num_channels=args.num_channels,
            requested_device=args.device,
        )
        torch.manual_seed(args.seed)
        _export(torch, model, args.output, args.opset)
        manifest = build_manifest(
            args.output,
            args.checkpoint,
            model_version=args.model_version,
            opset=args.opset,
            num_channels=args.num_channels,
            device=str(device),
            seed=args.seed,
        )
        write_json(manifest_path, manifest)
        print(f"exported {args.output}")
        print(f"wrote {manifest_path}")
        return 0
    except (CheckpointError, DependencyError, FileNotFoundError, RuntimeError, ValueError) as exc:
        print(f"export failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

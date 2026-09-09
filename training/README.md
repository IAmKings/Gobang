# AlphaZero model contract tools

These scripts reproduce the original `GomokuNNet` from `Nagi-ovo/alphazero-gomoku` for the fixed Android contract:

- input: `float32[1, 1, 15, 15]`, canonical values `0/+1/-1`
- outputs: `policy float32[1, 225]` (probabilities) and `value float32[1, 1]`
- action order: `row * 15 + col`
- value perspective: current canonical player

No checkpoint is committed. The tools fail without one instead of generating fake model assets. Use a virtualenv on the M4:

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r training/requirements.txt
python3 training/export_onnx.py \
  --checkpoint /path/to/best.pth.tar \
  --output artifacts/model.onnx \
  --device auto
python3 training/generate_golden.py \
  --checkpoint /path/to/best.pth.tar \
  --output artifacts/golden.json \
  --device auto
python3 training/validate_onnx.py \
  --model artifacts/model.onnx \
  --golden artifacts/golden.json \
  --manifest artifacts/model_manifest.json
```

`--device auto` selects MPS on Apple Silicon when available, then CUDA, then CPU. Export is finalized on CPU for portable ONNX output. A 9x9 checkpoint or a checkpoint trained with another `num_channels` value is rejected with a shape diagnostic; use `--num-channels` only when it matches the original 15x15 checkpoint.

## Contract-only 15x15 bootstrap

When no compatible 15x15 checkpoint is available, a short deterministic
supervised bootstrap can validate the artifact pipeline on the M4:

```bash
python3 training/bootstrap_train.py \
  --steps 2 \
  --output /private/tmp/alphazero-gomoku/bootstrap-smoke.pth.tar
```

This produces a `contract-smoke-only` checkpoint from immediate-win tactical
positions. It is not an AlphaZero self-play model and must not be shipped or
used for strength claims. Use it only with the export and golden commands
above to verify the 15x15 shapes and runtime wiring. Production quality still
requires a separate self-play training run.

## Upstream self-play smoke

The upstream loop was also run from commit
`11146dba12d7d5886cd3430567c71de9607297eb` in a temporary clone with one
15x15 episode, four MCTS simulations, 32 channels, one training iteration,
and one epoch. NumPy 2.x requires the compatibility change
`board.tostring()` → `board.tobytes()` in that temporary clone. The loop
completed and produced a 15x15 state dict; the resulting checkpoint exported
and passed 13 desktop golden cases. Because one arena game is not enough to
accept a new model, this remains a training smoke result, not a production
checkpoint. Evidence is in
`docs/benchmark/selfplay-smoke-15x15.json`.

Artifacts are intentionally generated outside source control: `model.onnx`, `model_manifest.json`, and `golden.json`. The generated manifest records SHA-256 hashes for both the source checkpoint and ONNX file. `training/model_manifest.json` is only the unexported contract template and must not be shipped as a model manifest.

## Server baseline launcher

The checked-in server baseline targets the original network at 64 channels on
15x15. Install the server dependencies in a CUDA-enabled virtualenv, clone the
upstream source at the commit recorded in `server-baseline.yaml`, and keep
checkpoints on persistent server storage:

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r training/server-requirements.txt
python3 training/launch_server_baseline.py \
  --source-dir /opt/alphazero-gomoku \
  --checkpoint-dir /mnt/checkpoints/gomoku-15x15-64ch \
  --profile pilot
```

The command is dry-run by default and prints the generated upstream config
and exact training command. Add `--run` only on the intended server. Profiles
are `smoke`, `pilot`, `production-preflight`, `quality-preflight`, and
`production`; generated configs and checkpoints are never written to Git. The
`production-preflight` profile validates the production compute shape for one
iteration. The `quality-preflight` profile uses three iterations, 50 games per
iteration, 40 arena games, 800 simulations, and five epochs to reduce the
chance that a small-sample overfit is promoted. The server dependency file
pins `numpy<2` because the upstream source calls the removed
`ndarray.tostring()` API.

## Statistical promotion evaluation

`evaluate_arena.py` compares two existing checkpoints without training or
changing either one. It requires an even game count because the upstream Arena
plays half the games with each model going first. A candidate passes only when
its decisive-game win rate is at least 55% and the lower endpoint of its 95%
Wilson interval is above 50%.

```bash
python3 training/evaluate_arena.py \
  --source-dir /opt/alphazero-gomoku \
  --source-commit 11146dba12d7d5886cd3430567c71de9607297eb \
  --config /mnt/checkpoints/gomoku-quality-preflight/upstream-quality-preflight.yaml \
  --candidate /mnt/checkpoints/gomoku-quality-preflight/best.pth.tar \
  --baseline /mnt/checkpoints/gomoku-pilot/best.pth.tar \
  --output /mnt/checkpoints/gomoku-quality-preflight/promotion-eval.json \
  --games 200 \
  --simulations 800
```

Use `--dry-run` first. The evidence output path must not already exist.

### Resumable local precheck

For a local, non-CUDA strength precheck, add `--resume-state` and
`--local-precheck`. The evaluator atomically updates the state JSON after each
completed game. Re-run the identical command after an interruption; it verifies
the pinned source, configuration, model hashes, score totals, and NumPy random
state before continuing from the next game. This mode starts every game with a
fresh MCTS tree so a restored process has the same per-game boundary as an
uninterrupted resumable run.

```bash
python3 training/evaluate_arena.py \
  --source-dir /path/to/alphazero-gomoku \
  --source-commit 11146dba12d7d5886cd3430567c71de9607297eb \
  --config /path/to/local-cpu.yaml \
  --candidate /path/to/quality-best.pth.tar \
  --baseline /path/to/pilot-best.pth.tar \
  --output /path/to/local-precheck-evidence.json \
  --resume-state /path/to/local-precheck-state.json \
  --local-precheck \
  --games 200 \
  --simulations 800
```

Local-precheck evidence is explicitly marked non-authoritative; it can block a
weak candidate early but cannot replace the CUDA promotion evaluation.

## Resumable local production training (M4 / MPS)

`resumable_local_train.py` is the local-only, interruption-safe adapter for
the existing 15x15 / 64-channel `production` profile. It retains the full
100 iterations × 100 self-play games × 800 MCTS simulations × 10 epochs × 40
Arena games budget. It does not modify the input quality checkpoint and its
output remains non-authoritative for the CUDA production gate.

Start it from the native macOS Terminal where MPS was verified (not from the
Codex-managed terminal). The runner prints JSON events immediately, including
the actual device (`mps` or `cpu`), per-game self-play progress, batch progress,
and the elapsed seconds for every epoch. It atomically persists each completed
self-play game, epoch, Arena game, and candidate-acceptance transaction.
Training optimizer states are named per iteration (`training-state-001.pth.tar`,
`training-state-002.pth.tar`, …), so a completed iteration can never block the
next one from training.

```bash
cd /Users/pauldeman/Documents/ai_workspace/gobang

# Use the same Python where `torch.backends.mps.is_available()` is true.
python3 -m pip install -r training/server-requirements.txt
SDL_VIDEODRIVER=dummy PYTHONUNBUFFERED=1 python3 training/resumable_local_train.py \
  --source-dir training/artifacts/promotion-gate/upstream \
  --input-checkpoint training/artifacts/promotion-gate/quality-best.pth.tar \
  --run-root training/artifacts/local-production-mps \
  --profile production \
  --device auto
```

On an M4 native Terminal, the initial event must contain `"device": "mps"`.
If it contains `"device": "cpu"`, stop before committing significant time and
check that the same `python3` reports `mps:0`. A normal shutdown or power loss
is resumed with the **identical** command; do not delete the run root.

Query the current durable progress and latest accepted/rejected result without
starting training:

```bash
python3 training/resumable_local_train.py \
  --source-dir training/artifacts/promotion-gate/upstream \
  --input-checkpoint training/artifacts/promotion-gate/quality-best.pth.tar \
  --run-root training/artifacts/local-production-mps \
  --profile production \
  --status
```

The status output reports phase, completed self-play games, completed epochs,
completed Arena games, actual last-used device, and the latest iteration
result. Current verification evidence is a CPU `smoke` run only (one game,
four simulations, one epoch): it completed its recovery path and is not a
strength result. The previously completed 200-game local precheck remains
129:71 (64.5%) for the quality checkpoint versus pilot, also non-authoritative.

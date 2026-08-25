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

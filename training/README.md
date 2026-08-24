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

Artifacts are intentionally generated outside source control: `model.onnx`, `model_manifest.json`, and `golden.json`. The generated manifest records SHA-256 hashes for both the source checkpoint and ONNX file. `training/model_manifest.json` is only the unexported contract template and must not be shipped as a model manifest.

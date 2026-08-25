# AlphaZero benchmark evidence

This directory stores reproducible benchmark inputs and release-gate evidence.
Do not record a latency claim without the model hash, runtime/EP, thread count,
candidate limit, simulation count, device and build variant.

Required result fields:

```json
{
  "commit": "...",
  "device": "...",
  "abi": "arm64-v8a",
  "build_type": "debug|release",
  "model_sha256": "...",
  "runtime": "onnxruntime-android ...",
  "execution_provider": "cpu|nnapi|...",
  "threads": 1,
  "candidate_limit": 20,
  "simulations": 96,
  "samples": 100,
  "p50_ms": 0.0,
  "p95_ms": 0.0,
  "peak_memory_mb": 0.0,
  "nn_calls_per_move": 0.0
}
```

The current MVP has a deterministic 1,000-position legality regression in
`AlphaZeroRandomPositionTest`. Android device latency and PyTorch → desktop
ORT → Android ORT golden results remain deferred until a real checkpoint and
an Android ARM64 device are available.

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
`AlphaZeroRandomPositionTest`. An ARM64 device is available and the app has
passed install/launch/gameplay smoke with a temporary 15x15 contract-smoke
model. Production model parity and latency remain deferred: the inspected
upstream Nagi-ovo checkpoint is 9x9 while this app's contract is fixed at
15x15. The smoke evidence is recorded in
`device-PJZ110-bootstrap-model-smoke.json`; its generated model is not
committed.

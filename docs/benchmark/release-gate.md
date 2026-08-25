# MVP release gate

| Gate | Status | Evidence / blocker |
|---|---|---|
| Shared engine rules, canonical values, tactical checks, PUCT and tree reuse | PASS | `:engine:jvmTest` |
| 1,000 deterministic legal-position searches | PASS | `AlphaZeroRandomPositionTest` |
| Android model manifest/hash validation | PASS | `:app:testDebugUnitTest` |
| M4 Android debug APK compilation | PASS | `assembleDebug` |
| 15x15 bootstrap checkpoint -> ONNX -> desktop ORT contract smoke | PASS | 13 golden cases; FP32 error below 1e-7 |
| Upstream 15x15 self-play/training loop smoke | PASS | 1 episode + 1 iteration on M4; state dict and ONNX contract valid |
| 64-channel server baseline launcher + contract smoke | PASS | Dry-run, 64-channel training smoke, checkpoint and ONNX golden valid |
| PyTorch/desktop ORT/Android ORT golden parity | DEFERRED | The inspected upstream `12.13best.pth.tar` is a 9x9 checkpoint (`fc3=81`); Android contract requires 15x15 (`fc3=225`) |
| Android ARM64 device install/launch/gameplay smoke | PASS | `device-PJZ110-bootstrap-model-smoke.json`; contract-smoke model only |
| Android emulator and ARM64 device performance benchmark | DEFERRED | No compatible 15x15 ONNX model asset; emulator run still unavailable |
| Ordinary difficulty P95 <= 2 s | DEFERRED | Requires model asset and device benchmark |
| Production-quality 15x15 self-play checkpoint | DEFERRED | Smoke run is intentionally tiny; arena had no decisive game and rejected the candidate |
| 20-minute ANR/leak soak | DEFERRED | Requires emulator/ARM64 device run |

JNI/NDK, quantization and NNAPI/GPU remain post-MVP experiments. No release
decision should be inferred from the deferred rows. The current Android
fallback path is intentional until a compatible 15x15 checkpoint is supplied
or trained.

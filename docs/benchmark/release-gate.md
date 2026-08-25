# MVP release gate

| Gate | Status | Evidence / blocker |
|---|---|---|
| Shared engine rules, canonical values, tactical checks, PUCT and tree reuse | PASS | `:engine:jvmTest` |
| 1,000 deterministic legal-position searches | PASS | `AlphaZeroRandomPositionTest` |
| Android model manifest/hash validation | PASS | `:app:testDebugUnitTest` |
| M4 Android debug APK compilation | PASS | `assembleDebug` |
| PyTorch/desktop ORT/Android ORT golden parity | DEFERRED | No real checkpoint/model asset in repository |
| Android ARM64 device install/launch/gameplay smoke | PASS | `device-PJZ110-smoke.json`; latency remains legacy-fallback only |
| Android emulator and ARM64 device performance benchmark | DEFERRED | No real ONNX model asset; emulator run still unavailable |
| Ordinary difficulty P95 <= 2 s | DEFERRED | Requires model asset and device benchmark |
| 20-minute ANR/leak soak | DEFERRED | Requires emulator/ARM64 device run |

JNI/NDK, quantization and NNAPI/GPU remain post-MVP experiments. No release
decision should be inferred from the deferred rows.

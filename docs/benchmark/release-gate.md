# MVP release gate

| Gate | Status | Evidence / blocker |
|---|---|---|
| Shared engine rules, canonical values, tactical checks, PUCT and tree reuse | PASS | `:engine:jvmTest` |
| 1,000 deterministic legal-position searches | PASS | `AlphaZeroRandomPositionTest` |
| Android model manifest/hash validation | PASS | `:app:testDebugUnitTest` |
| M4 Android debug APK compilation | PASS | `assembleDebug` |
| PyTorch/desktop ORT/Android ORT golden parity | DEFERRED | No real checkpoint/model asset in repository |
| Android emulator and ARM64 device benchmark | DEFERRED | Device run not available in this workspace |
| Ordinary difficulty P95 <= 2 s | DEFERRED | Requires model asset and device benchmark |
| 20-minute ANR/leak soak | DEFERRED | Requires emulator/ARM64 device run |

JNI/NDK, quantization and NNAPI/GPU remain post-MVP experiments. No release
decision should be inferred from the deferred rows.

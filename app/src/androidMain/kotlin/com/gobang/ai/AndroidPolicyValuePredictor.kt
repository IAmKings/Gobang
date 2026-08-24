package com.gobang.ai

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.gobang.engine.AlphaZeroBoard
import com.gobang.engine.PolicyValue
import com.gobang.engine.PolicyValuePredictor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android-only FP32 CPU adapter. The common engine only sees
 * [PolicyValuePredictor], so ONNX Runtime never leaks into shared code.
 */
class AndroidPolicyValuePredictor private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val manifest: AndroidModelManifest,
) : PolicyValuePredictor, AutoCloseable {
    override val modelId: String = manifest.modelId

    override fun predict(canonicalBoard: FloatArray): PolicyValue {
        require(canonicalBoard.size == AlphaZeroBoard.ACTION_SIZE) {
            "Canonical board must contain ${AlphaZeroBoard.ACTION_SIZE} values"
        }

        try {
            val inputBuffer = ByteBuffer
                .allocateDirect(canonicalBoard.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            inputBuffer.put(canonicalBoard).rewind()
            OnnxTensor.createTensor(
                environment,
                inputBuffer,
                longArrayOf(1, 1, BOARD_SIZE.toLong(), BOARD_SIZE.toLong()),
            ).use { inputTensor ->
                session.run(mapOf(manifest.inputName to inputTensor)).use { result ->
                    val policy = result.get(manifest.policyName).orElseThrow {
                        IllegalStateException("Missing policy output '${manifest.policyName}'")
                    }.value.flattenFloats("policy")
                    val value = result.get(manifest.valueName).orElseThrow {
                        IllegalStateException("Missing value output '${manifest.valueName}'")
                    }.value.flattenFloats("value")
                    require(policy.size == AlphaZeroBoard.ACTION_SIZE) {
                        "Policy output must contain ${AlphaZeroBoard.ACTION_SIZE} values, got ${policy.size}"
                    }
                    require(value.size == 1) { "Value output must contain one value, got ${value.size}" }
                    return PolicyValue(policy, value[0].coerceIn(-1f, 1f))
                }
            }
        } catch (error: Exception) {
            throw AndroidInferenceException("ONNX Runtime inference failed for '$modelId'", error)
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val BOARD_SIZE = 15
        const val DEFAULT_MODEL_ASSET = "models/model.onnx"
        const val DEFAULT_MANIFEST_ASSET = "models/model_manifest.json"

        /**
         * Returns a failure signal for CompositeSearcher to select its legacy
         * engine. No session is returned unless all checks have passed.
         */
        fun load(
            context: Context,
            modelAsset: String = DEFAULT_MODEL_ASSET,
            manifestAsset: String = DEFAULT_MANIFEST_ASSET,
        ): Result<AndroidPolicyValuePredictor> = runCatching {
            val modelBytes = context.assets.open(modelAsset).use { it.readBytes() }
            val manifestJson = context.assets.open(manifestAsset).use { it.bufferedReader().readText() }
            val manifest = AndroidModelManifestValidator.parseAndValidate(manifestJson, modelBytes)
            val environment = OrtEnvironment.getEnvironment()
            val session = try {
                environment.createSession(modelBytes)
            } catch (error: Exception) {
                throw AndroidModelLoadException("Unable to create ONNX Runtime session", error)
            }
            try {
                validateSession(session, manifest)
            } catch (error: Exception) {
                session.close()
                throw error
            }
            AndroidPolicyValuePredictor(environment, session, manifest)
        }.recoverCatching { error ->
            throw AndroidModelLoadException("Unable to load Android model assets", error)
        }

        private fun validateSession(session: OrtSession, manifest: AndroidModelManifest) {
            val inputInfo = session.inputInfo[manifest.inputName]?.getInfo()
                ?: error("Model input '${manifest.inputName}' is missing")
            val outputPolicyInfo = session.outputInfo[manifest.policyName]?.getInfo()
                ?: error("Model output '${manifest.policyName}' is missing")
            val outputValueInfo = session.outputInfo[manifest.valueName]?.getInfo()
                ?: error("Model output '${manifest.valueName}' is missing")
            require(inputInfo is TensorInfo && inputInfo.type == OnnxJavaType.FLOAT) {
                "Model input '${manifest.inputName}' must be float32 tensor"
            }
            require(outputPolicyInfo is TensorInfo && outputPolicyInfo.type == OnnxJavaType.FLOAT) {
                "Model output '${manifest.policyName}' must be float32 tensor"
            }
            require(outputValueInfo is TensorInfo && outputValueInfo.type == OnnxJavaType.FLOAT) {
                "Model output '${manifest.valueName}' must be float32 tensor"
            }
            require(inputInfo.getShape().contentEquals(longArrayOf(1, 1, 15, 15))) {
                "Model input shape must be [1, 1, 15, 15]"
            }
            require(outputPolicyInfo.getShape().contentEquals(longArrayOf(1, 225))) {
                "Model policy shape must be [1, 225]"
            }
            require(outputValueInfo.getShape().contentEquals(longArrayOf(1, 1))) {
                "Model value shape must be [1, 1]"
            }
        }
    }
}

class AndroidModelLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AndroidInferenceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun Any?.flattenFloats(name: String): FloatArray {
    val output = ArrayList<Float>()

    fun append(value: Any?) {
        when (value) {
            is FloatArray -> value.forEach(output::add)
            is Array<*> -> value.forEach(::append)
            is Number -> output += value.toFloat()
            else -> error("$name output is not a float tensor: ${value?.javaClass?.name}")
        }
    }

    append(this)
    return output.toFloatArray()
}

package com.gobang.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

/** The validated subset of the training/export contract consumed by Android. */
data class AndroidModelManifest(
    val modelId: String,
    val inputName: String,
    val policyName: String,
    val valueName: String,
)

/**
 * Validates the model before ORT sees it. A bad asset is a load failure, not a
 * partially initialized predictor that can fail later on the search thread.
 */
object AndroidModelManifestValidator {
    private const val CONTRACT_VERSION = 1
    private const val BOARD_SIZE = 15
    private const val ACTION_SIZE = BOARD_SIZE * BOARD_SIZE
    private const val RULES_VERSION = "gomoku-15-exact-five-v1"
    private val INPUT_SHAPE = listOf(1L, 1L, BOARD_SIZE.toLong(), BOARD_SIZE.toLong())
    private val POLICY_SHAPE = listOf(1L, ACTION_SIZE.toLong())
    private val VALUE_SHAPE = listOf(1L, 1L)

    fun parseAndValidate(manifestJson: String, modelBytes: ByteArray): AndroidModelManifest {
        val root = Json.parseToJsonElement(manifestJson).jsonObject
        val errors = mutableListOf<String>()

        if (root.int("contract_version") != CONTRACT_VERSION) {
            errors += "contract_version must be $CONTRACT_VERSION"
        }
        if (root.string("status") != "exported") errors += "status must be exported"
        if (root.int("board_size") != BOARD_SIZE) errors += "board_size must be $BOARD_SIZE"
        if (root.int("action_size") != ACTION_SIZE) errors += "action_size must be $ACTION_SIZE"
        if (root.string("action_order") != "row-major") errors += "action_order must be row-major"
        if (root.string("rules_version") != RULES_VERSION) errors += "rules_version must be $RULES_VERSION"
        if (root.string("value_perspective") != "current_player") {
            errors += "value_perspective must be current_player"
        }

        val modelId = root.string("model_version")
        if (modelId.isBlank()) errors += "model_version must be non-empty"

        val input = root.objectOrNull("input")
        val inputName = input.string("name")
        if (inputName.isBlank()) errors += "input.name must be non-empty"
        validateTensor(input, "input", INPUT_SHAPE, errors)
        val values = input.objectOrNull("values")
        if (values.int("empty") != 0 ||
            values.int("current_player") != 1 ||
            values.int("opponent") != -1
        ) {
            errors += "input.values must be empty=0, current_player=1, opponent=-1"
        }

        val outputs = root.objectOrNull("outputs")
        val policy = outputs.objectOrNull("policy")
        val value = outputs.objectOrNull("value")
        val policyName = policy.string("name")
        val valueName = value.string("name")
        if (policyName.isBlank()) errors += "outputs.policy.name must be non-empty"
        if (valueName.isBlank()) errors += "outputs.value.name must be non-empty"
        validateTensor(policy, "outputs.policy", POLICY_SHAPE, errors)
        validateTensor(value, "outputs.value", VALUE_SHAPE, errors)
        if (policy.string("semantic") != "probability") errors += "policy semantic must be probability"
        if (value.string("semantic") != "current_player") errors += "value semantic must be current_player"

        val expectedHash = root.string("model_sha256")
        val actualHash = sha256(modelBytes)
        if (!expectedHash.matches(Regex("[0-9a-fA-F]{64}"))) {
            errors += "model_sha256 must be a 64-character hexadecimal digest"
        } else if (!expectedHash.equals(actualHash, ignoreCase = true)) {
            errors += "model_sha256 does not match model bytes"
        }

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid Android model manifest: ${errors.joinToString("; ")}")
        }
        return AndroidModelManifest(modelId, inputName, policyName, valueName)
    }

    private fun validateTensor(
        tensor: JsonObject?,
        name: String,
        expectedShape: List<Long>,
        errors: MutableList<String>,
    ) {
        if (tensor == null) {
            errors += "$name must be an object"
            return
        }
        if (tensor.string("dtype") != "float32") errors += "$name.dtype must be float32"
        if (tensor.arrayOrNull("shape")?.toLongList() != expectedShape) {
            errors += "$name.shape must be $expectedShape"
        }
    }

    private fun JsonArray.toLongList(): List<Long> = map { (it as? JsonPrimitive)?.longOrNull ?: Long.MIN_VALUE }

    private fun JsonObject?.string(name: String): String = this?.get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject?.int(name: String): Int = this?.get(name)?.jsonPrimitive?.intOrNull ?: Int.MIN_VALUE

    private fun JsonObject?.objectOrNull(name: String): JsonObject? = this?.get(name)?.jsonObject

    private fun JsonObject?.arrayOrNull(name: String): JsonArray? = this?.get(name)?.jsonArray

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}

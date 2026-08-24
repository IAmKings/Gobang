package com.gobang.ai

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidModelManifestValidatorTest {
    @Test
    fun acceptsExportedManifestWhenHashMatches() {
        val model = "golden-model".encodeToByteArray()
        val manifest = manifest(hashOf(model))

        val parsed = AndroidModelManifestValidator.parseAndValidate(manifest, model)

        assertEquals("mvp-fp32-v1", parsed.modelId)
        assertEquals("canonical_board", parsed.inputName)
        assertEquals("policy", parsed.policyName)
        assertEquals("value", parsed.valueName)
    }

    @Test
    fun rejectsHashMismatchAndWrongTensorContract() {
        val manifest = manifest("0".repeat(64)).replace("[1, 1, 15, 15]", "[1, 15, 15]")

        val error = assertFailsWith<IllegalArgumentException> {
            AndroidModelManifestValidator.parseAndValidate(manifest, "model".encodeToByteArray())
        }

        assertEquals(true, error.message!!.contains("input.shape"))
        assertEquals(true, error.message!!.contains("model_sha256"))
    }

    private fun manifest(hash: String): String = """
        {
          "contract_version": 1,
          "status": "exported",
          "model_version": "mvp-fp32-v1",
          "board_size": 15,
          "action_size": 225,
          "action_order": "row-major",
          "rules_version": "gomoku-15-exact-five-v1",
          "input": {
            "name": "canonical_board",
            "shape": [1, 1, 15, 15],
            "dtype": "float32",
            "values": {"empty": 0, "current_player": 1, "opponent": -1}
          },
          "outputs": {
            "policy": {"name": "policy", "shape": [1, 225], "dtype": "float32", "semantic": "probability"},
            "value": {"name": "value", "shape": [1, 1], "dtype": "float32", "semantic": "current_player"}
          },
          "value_perspective": "current_player",
          "model_sha256": "$hash"
        }
    """.trimIndent()

    private fun hashOf(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

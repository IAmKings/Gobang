#!/usr/bin/env python3
"""Pure-stdlib contract checks; runnable before installing torch/ONNX."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.contract import (  # noqa: E402
    ACTION_SIZE,
    BOARD_SIZE,
    INPUT_SHAPE,
    POLICY_SHAPE,
    VALUE_SHAPE,
    action,
    expected_legal_argmax,
    fixture_boards,
    row_col,
    sha256_file,
    validate_manifest,
)
from training.validate_onnx import _outputs  # noqa: E402


class ContractTest(unittest.TestCase):
    def test_action_mapping_is_row_major(self) -> None:
        self.assertEqual(action(0, 0), 0)
        self.assertEqual(action(7, 7), 112)
        self.assertEqual(row_col(224), (14, 14))

    def test_fixture_contract_is_deterministic_and_valid(self) -> None:
        first = fixture_boards()
        second = fixture_boards()
        self.assertEqual(first, second)
        self.assertEqual(len(first), 13)
        for case in first:
            self.assertEqual(len(case["input"]), BOARD_SIZE)
            self.assertEqual(len(case["legal_mask"]), ACTION_SIZE)

    def test_legal_argmax_breaks_ties_by_lowest_action(self) -> None:
        policy = [0.0] * ACTION_SIZE
        mask = [0] * ACTION_SIZE
        mask[20] = mask[10] = 1
        policy[20] = policy[10] = 1.0
        self.assertEqual(expected_legal_argmax(policy, mask), 10)

    def test_manifest_template_is_explicitly_unexported(self) -> None:
        path = Path(__file__).resolve().parents[2] / "training" / "model_manifest.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(manifest["status"], "not-exported")
        self.assertEqual(manifest["input"]["shape"], INPUT_SHAPE)
        self.assertEqual(
            manifest["input"]["values"],
            {"empty": 0, "current_player": 1, "opponent": -1},
        )
        self.assertEqual(manifest["outputs"]["policy"]["shape"], POLICY_SHAPE)
        self.assertEqual(manifest["outputs"]["value"]["shape"], VALUE_SHAPE)
        self.assertTrue(validate_manifest(manifest))

    def test_sha256_is_stable(self) -> None:
        with tempfile.NamedTemporaryFile() as file:
            file.write(b"contract")
            file.flush()
            self.assertEqual(len(sha256_file(Path(file.name))), 64)

    def test_onnx_input_preserves_four_dimensional_shape(self) -> None:
        class Input:
            name = "canonical_board"

        class Session:
            def get_inputs(self):
                return [Input()]

            def run(self, names, inputs):
                board = inputs["canonical_board"]
                self.assert_shape = (len(board), len(board[0]), len(board[0][0]), len(board[0][0][0]))
                return [[0.0] * ACTION_SIZE], [[0.0]]

        session = Session()
        policy, value = _outputs(session, fixture_boards(1, 0)[0]["input"])
        self.assertEqual(session.assert_shape, (1, 1, BOARD_SIZE, BOARD_SIZE))
        self.assertEqual(len(policy), ACTION_SIZE)
        self.assertEqual(len(value), 1)


if __name__ == "__main__":
    unittest.main()

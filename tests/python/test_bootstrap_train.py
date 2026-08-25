import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.bootstrap_train import ACTION_SIZE, BOARD_SIZE, build_cases


class BootstrapTrainTest(unittest.TestCase):
    def test_cases_are_deterministic_and_15x15(self) -> None:
        first = build_cases(seed=7, count=4)
        second = build_cases(seed=7, count=4)
        self.assertEqual(first, second)
        for case in first:
            self.assertEqual(len(case.board), ACTION_SIZE)
            self.assertEqual(sum(value == 1.0 for value in case.board), 4)
            self.assertEqual(case.board[case.target_action], 0.0)
            self.assertGreaterEqual(case.target_action, 0)
            self.assertLess(case.target_action, BOARD_SIZE * BOARD_SIZE)
            self.assertEqual(case.target_value, 1.0)

    def test_count_must_be_positive(self) -> None:
        with self.assertRaises(ValueError):
            build_cases(count=0)


if __name__ == "__main__":
    unittest.main()

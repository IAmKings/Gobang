import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.evaluate_arena import (
    RESUME_STATE_VERSION,
    load_or_create_resume_state,
    promotion_decision,
    record_game_result,
    validate_contract,
    validate_games,
    validate_resume_state,
    wilson_interval,
    write_json,
)


class FakeRandom:
    def __init__(self) -> None:
        self.state = ("MT19937", [1, 2, 3], 4, 0, 0.0)

    def get_state(self):  # type: ignore[no-untyped-def]
        algorithm, keys, position, has_gauss, cached_gaussian = self.state
        return algorithm, FakeKeys(keys), position, has_gauss, cached_gaussian

    def set_state(self, state):  # type: ignore[no-untyped-def]
        self.state = state


class FakeKeys(list[int]):
    def tolist(self) -> list[int]:
        return list(self)


class FakeNumpy:
    def __init__(self) -> None:
        self.random = FakeRandom()

    @staticmethod
    def array(values, dtype: str):  # type: ignore[no-untyped-def]
        assert dtype == "uint32"
        return list(values)


class ArenaEvaluationTest(unittest.TestCase):
    def test_games_must_be_positive_and_even(self) -> None:
        validate_games(200)
        for games in (0, -2, 199):
            with self.assertRaises(ValueError):
                validate_games(games)

    def test_contract_is_fixed_to_cuda_15x15_64_channels(self) -> None:
        validate_contract(15, 64, True)
        for values in ((9, 64, True), (15, 32, True), (15, 64, False)):
            with self.assertRaises(ValueError):
                validate_contract(*values)
        validate_contract(15, 64, False, require_cuda=False)

    def test_wilson_interval_bounds(self) -> None:
        self.assertIsNone(wilson_interval(0, 0))
        lower, upper = wilson_interval(120, 200)
        self.assertLess(lower, 0.6)
        self.assertGreater(upper, 0.6)
        self.assertLess(lower, upper)

    def test_promotion_requires_rate_and_confidence(self) -> None:
        accepted = promotion_decision(120, 80, 0)
        self.assertEqual(accepted["status"], "PASS")
        self.assertGreater(accepted["wilson_95"]["lower"], 0.5)

        borderline = promotion_decision(21, 19, 0)
        self.assertEqual(borderline["status"], "BLOCKED")
        self.assertEqual(borderline["candidate_win_rate"], 0.525)

    def test_draw_only_match_is_blocked(self) -> None:
        result = promotion_decision(0, 0, 200)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertIsNone(result["wilson_95"])

    def test_resume_state_rejects_different_inputs_and_invalid_scores(self) -> None:
        identity = {"games": 200, "candidate_sha256": "candidate"}
        state = {
            "version": RESUME_STATE_VERSION,
            "identity": identity,
            "next_game_index": 3,
            "candidate_wins": 2,
            "baseline_wins": 1,
            "draws": 0,
            "numpy_rng_state": {},
        }
        validate_resume_state(state, identity)

        changed_identity = dict(identity)
        changed_identity["candidate_sha256"] = "other"
        with self.assertRaises(ValueError):
            validate_resume_state(state, changed_identity)

        state["draws"] = 1
        with self.assertRaises(ValueError):
            validate_resume_state(state, identity)

    def test_json_writes_are_atomic_and_replace_existing_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "state.json"
            write_json(output, {"completed_games": 1})
            write_json(output, {"completed_games": 2})
            self.assertEqual(output.read_text(encoding="utf-8"), '{\n  "completed_games": 2\n}\n')
            self.assertEqual(list(Path(directory).glob("*.tmp")), [])

    def test_resume_state_restores_rng_and_continues_after_completed_game(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            state_path = Path(directory) / "resume.json"
            identity = {"games": 2, "candidate_sha256": "candidate"}
            numpy = FakeNumpy()
            state = load_or_create_resume_state(state_path, identity, numpy)
            record_game_result(state, 1, numpy)
            write_json(state_path, state)

            numpy.random.state = ("changed", [9], 0, 0, 0.0)
            restored = load_or_create_resume_state(state_path, identity, numpy)
            self.assertEqual(restored["next_game_index"], 1)
            self.assertEqual(restored["candidate_wins"], 1)
            self.assertEqual(numpy.random.state, ("MT19937", [1, 2, 3], 4, 0, 0.0))


if __name__ == "__main__":
    unittest.main()

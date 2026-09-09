import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.resumable_local_train import (
    RUN_STATE_VERSION,
    TrainingRunError,
    atomic_write_bytes,
    atomic_pickle,
    build_identity,
    choose_runtime_device,
    create_or_load_state,
    finalize_pending_commit,
    new_run_state,
    record_arena_result,
    recover_arena_if_needed,
    recover_episode_if_needed,
    resolve_profile,
    status_payload,
    sha256_file,
    training_path,
    validate_run_state,
    write_json,
)


class FakeKeys(list[int]):
    def tolist(self) -> list[int]:
        return list(self)


class FakeRandom:
    def __init__(self) -> None:
        self.state = ("MT19937", [1, 2, 3], 4, 0, 0.0)

    def get_state(self):  # type: ignore[no-untyped-def]
        algorithm, keys, position, has_gauss, cached_gaussian = self.state
        return algorithm, FakeKeys(keys), position, has_gauss, cached_gaussian

    def set_state(self, state):  # type: ignore[no-untyped-def]
        self.state = state


class FakeNumpy:
    def __init__(self) -> None:
        self.random = FakeRandom()

    @staticmethod
    def array(values, dtype: str):  # type: ignore[no-untyped-def]
        assert dtype == "uint32"
        return list(values)


def profile_config() -> dict:
    return {
        "source": {"commit": "abc123"},
        "baseline": {
            "board_size": 15,
            "num_channels": 64,
            "dropout": 0.1,
            "min_lr": 0.0001,
            "max_lr": 0.001,
            "grad_clip": 1.0,
            "cpuct": 1.0,
        },
        "profiles": {
            "production": {
                "epochs": 10,
                "batch_size": 256,
                "num_iterations": 100,
                "num_episodes": 100,
                "max_queue_length": 200000,
                "num_iters_history": 20,
                "update_threshold": 0.55,
                "arena_compare": 40,
                "temp_threshold": 15,
                "num_sims": 800,
            }
        },
    }


class ResumableLocalTrainingTest(unittest.TestCase):
    def test_resolves_exact_production_profile(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        self.assertEqual(resolved["num_iterations"], 100)
        self.assertEqual(resolved["num_episodes"], 100)
        self.assertEqual(resolved["num_sims"], 800)
        self.assertEqual(resolved["num_channels"], 64)

    def test_run_state_rejects_changed_identity_and_invalid_arena_scores(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        identity = {"source_commit": "abc123", "input_checkpoint_sha256": "a" * 64}
        state = new_run_state(identity, resolved)
        validate_run_state(state, identity, resolved)
        with self.assertRaises(TrainingRunError):
            validate_run_state(state, {"source_commit": "other", "input_checkpoint_sha256": "a" * 64}, resolved)
        state["next_arena_game"] = 1
        with self.assertRaises(TrainingRunError):
            validate_run_state(state, identity, resolved)

    def test_creation_copies_input_without_mutating_it_and_status_is_machine_readable(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            checkpoint = root / "quality.pth.tar"
            checkpoint.write_bytes(b"immutable input")
            identity = build_identity(resolved, source, checkpoint, 7)
            run_root = root / "run"
            state = create_or_load_state(run_root, identity, resolved, checkpoint)
            self.assertEqual(state["version"], RUN_STATE_VERSION)
            self.assertEqual(checkpoint.read_bytes(), b"immutable input")
            self.assertEqual((run_root / "checkpoints" / "current.pth.tar").read_bytes(), b"immutable input")
            payload = status_payload(run_root, state, resolved)
            self.assertEqual(payload["event"], "training-status")
            self.assertEqual(payload["completed_episodes"], 0)
            self.assertIsNone(payload["latest_epoch"])
            self.assertFalse(payload["promotion_authority"])

    def test_atomic_write_replaces_only_complete_value(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "state.bin"
            atomic_write_bytes(output, b"first")
            atomic_write_bytes(output, b"second")
            self.assertEqual(output.read_bytes(), b"second")
            self.assertEqual(list(Path(directory).glob("*.tmp")), [])

    def test_arena_result_uses_training_run_counters(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        state = new_run_state({"input_checkpoint_sha256": "a" * 64}, resolved)

        class Numpy:
            class Random:
                @staticmethod
                def get_state():
                    return "MT19937", type("Keys", (list,), {"tolist": lambda self: list(self)})([1]), 0, 0, 0.0

            random = Random()

        record_arena_result(state, 1, Numpy())
        self.assertEqual(state["next_arena_game"], 1)
        self.assertEqual(state["candidate_wins"], 1)

    def test_auto_device_prefers_mps_and_reports_its_availability(self) -> None:
        class MpsBackend:
            @staticmethod
            def is_available() -> bool:
                return True

        class Torch:
            class Backends:
                mps = MpsBackend()

            backends = Backends()

            @staticmethod
            def device(name: str) -> str:
                return name

        device, availability = choose_runtime_device(Torch(), "auto")
        self.assertEqual(device, "mps")
        self.assertTrue(availability["mps_available"])

    def test_training_state_path_is_isolated_per_iteration(self) -> None:
        root = Path("/tmp/local-production")
        self.assertNotEqual(training_path(root, 1), training_path(root, 2))

    def test_pending_accepted_commit_is_idempotently_finalized_after_interruption(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            current = root / "checkpoints" / "current.pth.tar"
            candidate = root / "checkpoints" / "candidate-001.pth.tar"
            current.parent.mkdir(parents=True)
            current.write_bytes(b"baseline")
            candidate.write_bytes(b"candidate")
            state = new_run_state({"input_checkpoint_sha256": sha256_file(current)}, resolved)
            state.update(
                phase="arena",
                next_arena_game=40,
                candidate_wins=22,
                baseline_wins=18,
                candidate_checkpoint_sha256=sha256_file(candidate),
            )
            decision = {
                "iteration": 1,
                "status": "ACCEPTED",
                "candidate_sha256": sha256_file(candidate),
                "candidate_wins": 22,
                "baseline_wins": 18,
                "draws": 0,
                "candidate_win_rate": 0.55,
                "threshold": 0.55,
                "reason": "upstream update threshold met",
            }
            write_json(root / "commits" / "iteration-001.json", {"iteration": 1, "decision": decision})
            self.assertTrue(finalize_pending_commit(root, state, resolved))
            self.assertEqual(current.read_bytes(), b"candidate")
            self.assertEqual(state["iteration"], 2)
            self.assertEqual(state["phase"], "selfplay")
            self.assertEqual(state["current_checkpoint_sha256"], sha256_file(candidate))
            self.assertFalse((root / "commits" / "iteration-001.json").exists())

    def test_replay_transaction_recovers_when_file_precedes_main_state(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        with tempfile.TemporaryDirectory() as directory:
            state = new_run_state({"input_checkpoint_sha256": "a" * 64}, resolved)
            numpy = FakeNumpy()
            numpy.random.state = ("MT19937", [9, 8], 1, 0, 0.0)
            atomic_pickle(
                Path(directory) / "replay" / "iteration-001" / "episode-0000.pkl",
                {
                    "examples": ["completed game"],
                    "numpy_rng_state": {
                        "algorithm": "MT19937",
                        "keys": [9, 8],
                        "position": 1,
                        "has_gauss": 0,
                        "cached_gaussian": 0.0,
                    },
                },
            )
            upstream = type("Upstream", (), {"np": numpy})()
            self.assertTrue(recover_episode_if_needed(Path(directory), state, upstream))
            self.assertEqual(state["next_episode"], 1)
            self.assertEqual(numpy.random.state, ("MT19937", [9, 8], 1, 0, 0.0))

    def test_arena_snapshot_recovers_when_snapshot_precedes_main_state(self) -> None:
        resolved = resolve_profile(profile_config(), "production")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state = new_run_state({"input_checkpoint_sha256": "a" * 64}, resolved)
            state["phase"] = "arena"
            write_json(
                root / "arena" / "iteration-001-state.json",
                {
                    "iteration": 1,
                    "next_arena_game": 1,
                    "candidate_wins": 1,
                    "baseline_wins": 0,
                    "draws": 0,
                    "numpy_rng_state": {
                        "algorithm": "MT19937",
                        "keys": [5],
                        "position": 0,
                        "has_gauss": 0,
                        "cached_gaussian": 0.0,
                    },
                },
            )
            numpy = FakeNumpy()
            upstream = type("Upstream", (), {"np": numpy})()
            self.assertTrue(recover_arena_if_needed(root, state, upstream))
            self.assertEqual(state["next_arena_game"], 1)
            self.assertEqual(state["candidate_wins"], 1)
            self.assertEqual(numpy.random.state, ("MT19937", [5], 0, 0, 0.0))


if __name__ == "__main__":
    unittest.main()

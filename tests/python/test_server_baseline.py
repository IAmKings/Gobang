import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.launch_server_baseline import build_upstream_config, command_for, validate_config, verify_source_commit


def sample_config() -> dict:
    return {
        "source": {"commit": "abc123"},
        "baseline": {"board_size": 15, "num_channels": 64, "dropout": 0.1, "min_lr": 1e-4, "max_lr": 1e-3, "grad_clip": 1.0, "cpuct": 1.0},
        "profiles": {"pilot": {
            "epochs": 5, "batch_size": 128, "num_iterations": 5, "num_episodes": 20,
            "max_queue_length": 50000, "num_iters_history": 5, "update_threshold": 0.55,
            "arena_compare": 20, "temp_threshold": 15, "num_sims": 128,
        }, "production-preflight": {
            "epochs": 10, "batch_size": 256, "num_iterations": 1, "num_episodes": 20,
            "max_queue_length": 200000, "num_iters_history": 20, "update_threshold": 0.55,
            "arena_compare": 20, "temp_threshold": 15, "num_sims": 800,
        }, "quality-preflight": {
            "epochs": 5, "batch_size": 256, "num_iterations": 3, "num_episodes": 50,
            "max_queue_length": 200000, "num_iters_history": 20, "update_threshold": 0.55,
            "arena_compare": 40, "temp_threshold": 15, "num_sims": 800,
        }},
    }


class ServerBaselineTest(unittest.TestCase):
    def test_builds_fixed_15x15_64_channel_upstream_config(self) -> None:
        result = build_upstream_config(sample_config(), "pilot", Path("/tmp/checkpoints"))
        self.assertEqual(result["game"]["board_size"], 15)
        self.assertEqual(result["network"]["num_channels"], 64)
        self.assertEqual(result["mcts"]["num_sims"], 128)
        self.assertEqual(result["system"]["checkpoint_dir"], "/tmp/checkpoints")

    def test_rejects_wrong_contract(self) -> None:
        config = sample_config()
        config["baseline"]["board_size"] = 9
        with self.assertRaises(ValueError):
            validate_config(config, "pilot")

    def test_command_is_safe_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            (source_dir / "alphazero.py").touch()
            command = command_for(source_dir, Path("/tmp/pilot.yaml"), "/usr/bin/python3")
        self.assertEqual(command[-3:], ["--train", "--config", "/tmp/pilot.yaml"])

    def test_builds_warm_start_config_for_production_preflight(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            checkpoint = root / "pilot" / "best.pth.tar"
            checkpoint.parent.mkdir()
            checkpoint.write_bytes(b"checkpoint")
            result = build_upstream_config(
                sample_config(),
                "production-preflight",
                root / "preflight",
                resume_from=checkpoint,
            )
        self.assertEqual(result["training"]["batch_size"], 256)
        self.assertEqual(result["mcts"]["num_sims"], 800)
        self.assertTrue(result["system"]["load_model"])
        self.assertEqual(result["system"]["load_folder_file"], [str(checkpoint.parent), checkpoint.name])

    def test_builds_quality_preflight_config(self) -> None:
        result = build_upstream_config(sample_config(), "quality-preflight", Path("/tmp/quality"))
        self.assertEqual(result["training"]["num_iterations"], 3)
        self.assertEqual(result["training"]["num_episodes"], 50)
        self.assertEqual(result["training"]["epochs"], 5)
        self.assertEqual(result["training"]["arena_compare"], 40)
        self.assertEqual(result["mcts"]["num_sims"], 800)

    @patch("training.launch_server_baseline.subprocess.run")
    def test_source_commit_is_verified(self, run) -> None:
        run.return_value.returncode = 0
        run.return_value.stdout = "abc123\n"
        verify_source_commit(Path("/tmp/upstream"), "abc123")
        run.return_value.stdout = "wrong\n"
        with self.assertRaises(ValueError):
            verify_source_commit(Path("/tmp/upstream"), "abc123")


if __name__ == "__main__":
    unittest.main()

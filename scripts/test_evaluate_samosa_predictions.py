import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class EvaluateSamosaPredictionsTest(unittest.TestCase):
    def test_metrics_and_server_agreement(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            predictions = root / "predictions.csv"
            manifest = root / "manifest.csv"
            server = root / "server.csv"
            output = root / "metrics.json"

            with predictions.open("w", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(["window_id", "true_label", "predicted_class", "phase"])
                writer.writerows([[0, 0, 0, "measured"], [1, 18, 18, "measured"], [2, 1, 18, "measured"]])
            with manifest.open("w", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(["window_id", "evaluation_index", "context"])
                writer.writerows([[0, 0, "Kitchen"], [1, 18, "Misc"], [2, 1, "Kitchen"]])
            with server.open("w", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(["window_id", "predicted_class"])
                writer.writerows([[0, 0], [1, 18], [2, 18]])

            subprocess.run([
                sys.executable, str(Path(__file__).with_name("evaluate_samosa_predictions.py")),
                str(predictions), "--window-manifest", str(manifest),
                "--server-predictions", str(server), "--output", str(output),
            ], check=True, capture_output=True, text=True)
            result = json.loads(output.read_text())
            self.assertEqual(result["windows"], 3)
            self.assertAlmostEqual(result["window_accuracy"], 2 / 3)
            self.assertEqual(result["server_watch_agreement"], 1.0)
            self.assertAlmostEqual(result["activity_vs_other_f1"], 2 / 3)


if __name__ == "__main__":
    unittest.main()

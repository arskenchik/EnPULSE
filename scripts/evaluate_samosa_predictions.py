#!/usr/bin/env python3
"""Compute the Week 3 Table A-1 metrics from labeled replay predictions."""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path


def safe_div(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def f1_for_label(y_true: list[int], y_pred: list[int], label: int) -> float:
    tp = sum(t == label and p == label for t, p in zip(y_true, y_pred))
    fp = sum(t != label and p == label for t, p in zip(y_true, y_pred))
    fn = sum(t == label and p != label for t, p in zip(y_true, y_pred))
    precision = safe_div(tp, tp + fp)
    recall = safe_div(tp, tp + fn)
    return safe_div(2 * precision * recall, precision + recall)


def binary_activity_f1(y_true: list[int], y_pred: list[int]) -> float:
    true_activity = [int(value != 18) for value in y_true]
    pred_activity = [int(value != 18) for value in y_pred]
    return f1_for_label(true_activity, pred_activity, 1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("predictions", type=Path)
    parser.add_argument("--window-manifest", type=Path, required=True)
    parser.add_argument("--server-predictions", type=Path)
    parser.add_argument("--output", type=Path, default=Path("table_a1_metrics.json"))
    args = parser.parse_args()

    with args.predictions.open(newline="", encoding="utf-8-sig") as handle:
        prediction_rows = list(csv.DictReader(handle))
    with args.window_manifest.open(newline="", encoding="utf-8-sig") as handle:
        manifest = {int(row["window_id"]): row for row in csv.DictReader(handle)}

    measured = [row for row in prediction_rows if row.get("phase", "measured") == "measured"]
    ids = [int(row["window_id"]) for row in measured]
    if len(ids) != len(set(ids)):
        raise ValueError("Accuracy input contains repeated window_id values; use one accuracy-mode run")
    missing = sorted(set(ids) - set(manifest))
    if missing:
        raise ValueError(f"Prediction IDs absent from manifest: {missing[:5]}")
    y_true = [int(row["true_label"]) for row in measured]
    y_pred = [int(row["predicted_class"]) for row in measured]
    if any(int(manifest[i]["evaluation_index"]) != target for i, target in zip(ids, y_true)):
        raise ValueError("Replay true labels do not agree with the authoritative window manifest")

    context_pairs: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for window_id, target, predicted in zip(ids, y_true, y_pred):
        context_pairs[manifest[window_id]["context"]].append((target, predicted))
    context_accuracy = {
        context: safe_div(sum(t == p for t, p in pairs), len(pairs))
        for context, pairs in sorted(context_pairs.items())
    }
    labels = list(range(19))
    per_class_f1 = {str(label): f1_for_label(y_true, y_pred, label) for label in labels}

    result = {
        "windows": len(y_true),
        "window_accuracy": safe_div(sum(t == p for t, p in zip(y_true, y_pred)), len(y_true)),
        "macro_f1_19_class": sum(per_class_f1.values()) / len(labels),
        "per_class_f1": per_class_f1,
        "context_accuracy": context_accuracy,
        "context_wise_accuracy_macro": safe_div(sum(context_accuracy.values()), len(context_accuracy)),
        "activity_vs_other_f1": binary_activity_f1(y_true, y_pred),
    }

    if args.server_predictions:
        with args.server_predictions.open(newline="", encoding="utf-8-sig") as handle:
            server = {int(row["window_id"]): int(row["predicted_class"]) for row in csv.DictReader(handle)}
        absent = sorted(set(ids) - set(server))
        if absent:
            raise ValueError(f"Server predictions missing IDs: {absent[:5]}")
        result["server_watch_agreement"] = safe_div(
            sum(server[i] == predicted for i, predicted in zip(ids, y_pred)), len(ids)
        )

    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

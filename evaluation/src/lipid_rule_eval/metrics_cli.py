"""Command-line entry point for reference matching and evaluation metrics."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from lipid_rule_eval.metrics.evaluator import FEDOROVA_RT_COLUMNS, evaluate


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create the manuscript-defined lipid evaluation workbook."
    )
    parser.add_argument("--results", required=True, type=Path, help="Final scored JSON")
    parser.add_argument("--gold-standard", required=True, type=Path, help="Reference XLSX")
    parser.add_argument("--output", required=True, type=Path, help="Output XLSX")
    parser.add_argument(
        "--mz-tolerance-ppm", type=float, default=20.0,
        help="Maximum absolute m/z error relative to reference m/z (default: 20 ppm)",
    )
    parser.add_argument("--rt-tolerance-seconds", type=float, default=6.0)
    parser.add_argument(
        "--gold-format", choices=("auto", "martinez", "fedorova"), default="auto",
        help="Reference workbook layout (default: detect from sheet names)",
    )
    parser.add_argument(
        "--reference-method", choices=tuple(FEDOROVA_RT_COLUMNS),
        help="Required for Fedorova: select the acquisition method's RT column",
    )
    args = parser.parse_args()
    report = evaluate(
        args.results,
        args.gold_standard,
        args.output,
        args.mz_tolerance_ppm,
        args.rt_tolerance_seconds,
        args.gold_format,
        args.reference_method,
    )
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

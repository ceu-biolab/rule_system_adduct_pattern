"""Command-line entry point for the preprocessing pilot."""

from __future__ import annotations

import argparse
from pathlib import Path

from lipid_rule_eval.artifacts.results import write_results
from lipid_rule_eval.config import load_config
from lipid_rule_eval.grouping.correlation_graph import group_features
from lipid_rule_eval.preprocessing.openms_pipeline import process_inputs


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run pyOpenMS preprocessing and chromatographic feature grouping"
    )
    parser.add_argument("--config", required=True, type=Path, help="Pilot JSON configuration")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    config = load_config(args.config)
    print(f"Processing {len(config.inputs)} mzML files sequentially")
    features, quality_control = process_inputs(config.inputs, config.preprocessing)
    print(f"Detected {len(features)} exported ion features")
    groups, edges = group_features(features, config.grouping)
    json_path, csv_path = write_results(
        config, features, groups, edges, quality_control
    )
    print(f"Wrote {json_path}")
    print(f"Wrote {csv_path}")


if __name__ == "__main__":
    main()

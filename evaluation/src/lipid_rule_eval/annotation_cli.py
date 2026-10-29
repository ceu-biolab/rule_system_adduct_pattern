"""Command-line entry point for adduct and compound-mass annotation."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from lipid_rule_eval.annotation.pipeline import run_annotation
from lipid_rule_eval.config import load_annotation_config


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Annotate grouped features through the Java API and CMM mass search"
    )
    parser.add_argument("--config", required=True, type=Path, help="Evaluation JSON configuration")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    config = load_annotation_config(args.config)
    print(f"Reading preprocessing groups from {config.input_json}")
    json_path, csv_path, summary = run_annotation(config)
    print(json.dumps(summary, indent=2))
    print(f"Wrote {json_path}")
    print(f"Wrote {csv_path}")


if __name__ == "__main__":
    main()

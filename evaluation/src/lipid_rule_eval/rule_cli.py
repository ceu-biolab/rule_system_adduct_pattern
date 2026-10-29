"""Command-line entry point for final expert-system rule execution."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from lipid_rule_eval.config import load_rule_execution_config
from lipid_rule_eval.rules.pipeline import run_rule_execution


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Apply class-specific Drools rules to enriched annotations"
    )
    parser.add_argument("--config", required=True, type=Path, help="Evaluation JSON configuration")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    config = load_rule_execution_config(args.config)
    print(f"Reading enriched annotations from {config.input_json}")
    json_path, csv_path, scores_path, summary = run_rule_execution(config)
    print(json.dumps(summary, indent=2))
    print(f"Wrote {json_path}")
    print(f"Wrote {csv_path}")
    print(f"Wrote {scores_path}")
    if summary["api_error_requests"] or summary["reconciliation_error_requests"]:
        raise SystemExit("Rule execution completed with request errors; inspect the JSON artifact")


if __name__ == "__main__":
    main()

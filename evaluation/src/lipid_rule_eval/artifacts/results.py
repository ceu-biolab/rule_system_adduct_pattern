"""Write flat CSV and nested JSON preprocessing results."""

from __future__ import annotations

import csv
import hashlib
import json
import platform
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

import pyopenms as oms

from lipid_rule_eval.config import EvaluationConfig
from lipid_rule_eval.models import CorrelationEdge, FeatureGroup, IonFeature


def sha256(path: Path) -> str:
    """Calculate an input checksum without modifying the raw file."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_results(
    config: EvaluationConfig,
    features: list[IonFeature],
    groups: list[FeatureGroup],
    edges: list[CorrelationEdge],
    quality_control: list[dict[str, object]],
) -> tuple[Path, Path]:
    """Write one JSON document and one flat CSV containing every feature."""
    config.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = config.output_dir / "feature_groups.csv"
    json_path = config.output_dir / "feature_groups.json"
    records = [
        feature.result_record(group.group_id, len(group.features))
        for group in groups
        for feature in group.features
    ]
    if records:
        with csv_path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(records[0]))
            writer.writeheader()
            writer.writerows(records)
    else:
        csv_path.write_text("", encoding="utf-8")

    correlated_groups = sum(len(group.features) > 1 for group in groups)
    feature_to_group = {
        feature.feature_id: group.group_id
        for group in groups
        for feature in group.features
    }
    edge_records = [
        {
            "left_feature_id": features[edge.left_index].feature_id,
            "right_feature_id": features[edge.right_index].feature_id,
            "polarity": features[edge.left_index].polarity,
            "pearson_correlation": edge.correlation,
            "rt_overlap": edge.rt_overlap,
            "common_points": edge.common_points,
            "same_final_group": (
                feature_to_group[features[edge.left_index].feature_id]
                == feature_to_group[features[edge.right_index].feature_id]
            ),
        }
        for edge in edges
    ]
    per_polarity = {}
    for polarity in ("POSITIVE", "NEGATIVE"):
        polarity_groups = [
            group for group in groups if group.features[0].polarity == polarity
        ]
        per_polarity[polarity] = {
            "exported_features": sum(len(group.features) for group in polarity_groups),
            "all_groups_including_singletons": len(polarity_groups),
            "correlated_multi_feature_groups": sum(
                len(group.features) > 1 for group in polarity_groups
            ),
            "singleton_groups": sum(
                len(group.features) == 1 for group in polarity_groups
            ),
            "threshold_passing_correlation_edges": sum(
                record["polarity"] == polarity for record in edge_records
            ),
        }
    payload = {
        "schema_version": "1.0",
        "dataset_id": config.dataset_id,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "stage": "preprocessing_and_feature_grouping",
        "annotation_performed": False,
        "rule_execution_performed": False,
        "software": {
            "python": platform.python_version(),
            "pyopenms": oms.__version__,
        },
        "inputs": [
            {
                "path": str(item.path),
                "file_name": item.path.name,
                "polarity": item.polarity,
                "size_bytes": item.path.stat().st_size,
                "sha256": sha256(item.path),
            }
            for item in config.inputs
        ],
        "parameters": {
            "preprocessing": asdict(config.preprocessing),
            "grouping": asdict(config.grouping),
            "future_annotation_context": config.future_annotation_context,
        },
        "quality_control": quality_control,
        "summary": {
            "exported_features": len(records),
            "all_groups_including_singletons": len(groups),
            "correlated_multi_feature_groups": correlated_groups,
            "singleton_groups": len(groups) - correlated_groups,
            "threshold_passing_correlation_edges": len(edges),
            "per_polarity": per_polarity,
        },
        "groups": [
            {
                "group_id": group.group_id,
                "polarity": group.features[0].polarity,
                "group_size": len(group.features),
                "is_correlated_group": len(group.features) > 1,
                "features": [
                    feature.result_record(group.group_id, len(group.features))
                    for feature in group.features
                ],
            }
            for group in groups
        ],
        "correlation_edges": edge_records,
    }
    json_path.write_text(json.dumps(payload, indent=2, allow_nan=False), encoding="utf-8")
    return json_path, csv_path

"""Orchestration and artifacts for the mass-only annotation stage."""

from __future__ import annotations

import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from lipid_rule_eval.annotation.api_client import AnnotationApiClient
from lipid_rule_eval.annotation.database import CompoundRepository
from lipid_rule_eval.config import AnnotationConfig


CSV_FIELDS = [
    "group_id", "source_file", "polarity", "group_size", "status",
    "hypothesis_id", "neutral_mass", "matched_signal_count", "adduct_labels",
    "compound_match_count", "compound_id", "cas_id", "compound_name", "formula",
    "database_mass", "mass_error_da", "mass_error_ppm", "charge_type",
    "charge_number", "formula_type", "compound_type", "compound_status",
    "formula_type_int", "logp", "rt_pred", "cmm_lipid_type",
    "cmm_lipid_type_rt", "cmm_lipid_type_fragmentation", "cmm_num_chains",
    "cmm_number_carbons", "cmm_double_bonds", "name_lipid_type", "lipid_type",
    "lipid_type_source", "rule_target", "lipid_type_conflict",
]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def annotate_payload(
    preprocessing: dict[str, Any],
    api_client: AnnotationApiClient,
    repository: CompoundRepository,
) -> list[dict[str, Any]]:
    """Annotate multi-feature groups and attach mass-only compound candidates."""
    annotated_groups = []
    for group in preprocessing["groups"]:
        group_result = {
            "group_id": group["group_id"],
            "source_files": sorted(
                {feature["source_file"] for feature in group["features"]}
            ),
            "polarity": group["polarity"],
            "group_size": group["group_size"],
            "features": group["features"],
            "status": "SKIPPED_SINGLETON",
            "hypotheses": [],
        }
        if group["group_size"] > 1:
            hypotheses = api_client.annotate(group)
            for hypothesis in hypotheses:
                hypothesis["compound_matches"] = repository.find_by_neutral_mass(
                    hypothesis["neutral_mass"]
                )
            group_result["hypotheses"] = hypotheses
            group_result["status"] = "ANNOTATED" if hypotheses else "NO_HYPOTHESIS"
        annotated_groups.append(group_result)
    return annotated_groups


def _summary(groups: list[dict[str, Any]]) -> dict[str, Any]:
    hypotheses = [
        hypothesis for group in groups for hypothesis in group["hypotheses"]
    ]
    compound_ids = {
        match["compound_id"]
        for hypothesis in hypotheses
        for match in hypothesis["compound_matches"]
    }
    matches = [
        match
        for hypothesis in hypotheses
        for match in hypothesis["compound_matches"]
    ]
    summary: dict[str, Any] = {
        "all_groups": len(groups),
        "submitted_multi_feature_groups": sum(
            group["group_size"] > 1 for group in groups
        ),
        "skipped_singleton_groups": sum(
            group["status"] == "SKIPPED_SINGLETON" for group in groups
        ),
        "groups_with_hypotheses": sum(
            group["status"] == "ANNOTATED" for group in groups
        ),
        "groups_without_hypotheses": sum(
            group["status"] == "NO_HYPOTHESIS" for group in groups
        ),
        "neutral_mass_hypotheses": len(hypotheses),
        "hypotheses_with_compound_matches": sum(
            bool(hypothesis["compound_matches"]) for hypothesis in hypotheses
        ),
        "compound_match_rows": sum(
            len(hypothesis["compound_matches"]) for hypothesis in hypotheses
        ),
        "unique_compound_ids": len(compound_ids),
        "compound_matches_with_lipid_type": sum(
            match.get("lipid_type") is not None for match in matches
        ),
        "compound_matches_classified_by_cmm": sum(
            match.get("lipid_type_source") == "CMM" for match in matches
        ),
        "compound_matches_classified_by_name_fallback": sum(
            match.get("lipid_type_source") == "COMPOUND_NAME" for match in matches
        ),
        "compound_matches_with_supported_rule_target": sum(
            match.get("rule_target") is not None for match in matches
        ),
        "lipid_type_conflicts": sum(
            bool(match.get("lipid_type_conflict")) for match in matches
        ),
        "rule_target_counts": _rule_target_counts(matches),
    }
    summary["per_polarity"] = {
        polarity: _summary_for_polarity(groups, polarity)
        for polarity in ("POSITIVE", "NEGATIVE")
    }
    return summary


def _rule_target_counts(matches: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for match in matches:
        target = match.get("rule_target")
        if target is not None:
            counts[target] = counts.get(target, 0) + 1
    return dict(sorted(counts.items()))


def _summary_for_polarity(
    groups: list[dict[str, Any]], polarity: str
) -> dict[str, int]:
    selected = [group for group in groups if group["polarity"] == polarity]
    hypotheses = [
        hypothesis for group in selected for hypothesis in group["hypotheses"]
    ]
    return {
        "submitted_multi_feature_groups": sum(group["group_size"] > 1 for group in selected),
        "groups_with_hypotheses": sum(group["status"] == "ANNOTATED" for group in selected),
        "groups_without_hypotheses": sum(group["status"] == "NO_HYPOTHESIS" for group in selected),
        "neutral_mass_hypotheses": len(hypotheses),
        "hypotheses_with_compound_matches": sum(bool(item["compound_matches"]) for item in hypotheses),
        "compound_match_rows": sum(len(item["compound_matches"]) for item in hypotheses),
    }


def _csv_rows(groups: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows = []
    for group in groups:
        base = {
            "group_id": group["group_id"],
            "source_file": ";".join(group["source_files"]),
            "polarity": group["polarity"],
            "group_size": group["group_size"],
            "status": group["status"],
        }
        if not group["hypotheses"]:
            rows.append(base)
            continue
        for hypothesis in group["hypotheses"]:
            labels = ";".join(
                f"{item['feature_id']}={item['adduct_name']}"
                for item in hypothesis["items"]
                if item["adduct_name"] is not None
            )
            hypothesis_fields = {
                "hypothesis_id": hypothesis["hypothesis_id"],
                "neutral_mass": hypothesis["neutral_mass"],
                "matched_signal_count": hypothesis["matched_signal_count"],
                "adduct_labels": labels,
                "compound_match_count": len(hypothesis["compound_matches"]),
            }
            if not hypothesis["compound_matches"]:
                rows.append(base | hypothesis_fields)
                continue
            for match in hypothesis["compound_matches"]:
                rows.append(
                    base
                    | hypothesis_fields
                    | {
                        "compound_id": match["compound_id"],
                        "cas_id": match["cas_id"],
                        "compound_name": match["compound_name"],
                        "formula": match["formula"],
                        "database_mass": match["mass"],
                        "mass_error_da": match["mass_error_da"],
                        "mass_error_ppm": match["mass_error_ppm"],
                        "charge_type": match["charge_type"],
                        "charge_number": match["charge_number"],
                        "formula_type": match["formula_type"],
                        "compound_type": match["compound_type"],
                        "compound_status": match["compound_status"],
                        "formula_type_int": match["formula_type_int"],
                        "logp": match["logp"],
                        "rt_pred": match["rt_pred"],
                        "cmm_lipid_type": match["cmm_lipid_type"],
                        "cmm_lipid_type_rt": match["cmm_lipid_type_rt"],
                        "cmm_lipid_type_fragmentation": match["cmm_lipid_type_fragmentation"],
                        "cmm_num_chains": match["cmm_num_chains"],
                        "cmm_number_carbons": match["cmm_number_carbons"],
                        "cmm_double_bonds": match["cmm_double_bonds"],
                        "name_lipid_type": match["name_lipid_type"],
                        "lipid_type": match["lipid_type"],
                        "lipid_type_source": match["lipid_type_source"],
                        "rule_target": match["rule_target"],
                        "lipid_type_conflict": match["lipid_type_conflict"],
                    }
                )
    return rows


def run_annotation(config: AnnotationConfig) -> tuple[Path, Path, dict[str, Any]]:
    """Execute API annotation and database lookup from preprocessing JSON."""
    preprocessing = json.loads(config.input_json.read_text(encoding="utf-8"))
    if preprocessing.get("dataset_id") != config.dataset_id:
        raise ValueError("Preprocessing dataset_id does not match annotation config")
    if preprocessing.get("stage") != "preprocessing_and_feature_grouping":
        raise ValueError("Annotation input is not a preprocessing/grouping artifact")

    api_client = AnnotationApiClient(config.api)
    with CompoundRepository(config.database) as repository:
        database_metadata = repository.connection_metadata()
        groups = annotate_payload(preprocessing, api_client, repository)

    payload = {
        "schema_version": "1.0",
        "dataset_id": config.dataset_id,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "stage": "adduct_annotation_and_compound_mass_search",
        "annotation_performed": True,
        "compound_mass_search_performed": True,
        "rule_execution_performed": False,
        "input": {
            "path": str(config.input_json),
            "sha256": _sha256(config.input_json),
        },
        "parameters": {
            "annotation_api": {
                "endpoint": f"{config.api.base_url.rstrip('/')}/api/annotate-feature",
                "tolerance_mode": config.api.tolerance_mode,
                "tolerance": config.api.tolerance,
            },
            "compound_lookup": {
                "database": database_metadata["database_name"],
                "database_user": database_metadata["database_user"],
                "schema": config.database.schema,
                "table": config.database.table,
                "mass_tolerance_ppm": config.database.mass_tolerance_ppm,
                "filtering": "mass > 0 only; no categorical filters",
                "lipid_type_precedence": "CMM classification, then compound-name fallback",
            },
        },
        "summary": _summary(groups),
        "groups": groups,
    }

    config.output_dir.mkdir(parents=True, exist_ok=True)
    json_path = config.output_dir / "compound_annotations.json"
    csv_path = config.output_dir / "compound_annotations.csv"
    json_path.write_text(json.dumps(payload, indent=2, allow_nan=False), encoding="utf-8")
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(_csv_rows(groups))
    return json_path, csv_path, payload["summary"]

"""Plan, execute, reconcile, and serialize expert-system rule scores."""

from __future__ import annotations

import csv
import hashlib
import json
import statistics
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from lipid_rule_eval.annotation.pipeline import CSV_FIELDS as ANNOTATION_CSV_FIELDS
from lipid_rule_eval.annotation.pipeline import _csv_rows as annotation_csv_rows
from lipid_rule_eval.config import RuleExecutionConfig
from lipid_rule_eval.rules.api_client import RuleApiClient, RuleApiError


RULE_SCORE_FIELDS = [
    "request_id", "group_id", "polarity", "hypothesis_id", "neutral_mass",
    "rule_target", "candidate_match_count", "execution_status", "score",
    "normalized_score", "has_rule_evidence", "evidence_status",
    "applied_presence", "applied_intensity", "descr_correct", "descr_incorrect",
]

FINAL_SCORE_FIELDS = [
    "rule_request_id", "rule_execution_status", "rule_score",
    "rule_normalized_score", "rule_has_evidence", "rule_evidence_status",
    "rule_applied_presence", "rule_applied_intensity", "rule_descr_correct",
    "rule_descr_incorrect",
]


def sha256(path: Path) -> str:
    """Hash an input or rule workbook for execution provenance."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def plan_rule_requests(annotation: dict[str, Any]) -> list[dict[str, Any]]:
    """Deduplicate compound candidates into group/class API requests."""
    plans = []
    for group in annotation["groups"]:
        by_target: dict[str, dict[str, int]] = {}
        for hypothesis in group["hypotheses"]:
            counts: dict[str, int] = {}
            for match in hypothesis["compound_matches"]:
                target = match.get("rule_target")
                if target:
                    counts[target] = counts.get(target, 0) + 1
            for target, count in counts.items():
                by_target.setdefault(target, {})[hypothesis["hypothesis_id"]] = count
        for target in sorted(by_target):
            plans.append(
                {
                    "group": group,
                    "group_id": group["group_id"],
                    "polarity": group["polarity"],
                    "rule_target": target,
                    "expected_hypotheses": by_target[target],
                }
            )
    return plans


def _request_id(config: RuleExecutionConfig, plan: dict[str, Any]) -> str:
    identity = "|".join(
        (
            config.dataset_id,
            plan["group_id"],
            plan["rule_target"],
            config.tolerance_mode,
            str(config.tolerance),
            ",".join(config.mobile_phases),
            config.sample_type,
        )
    )
    return "rule-request-" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:24]


def execute_rule_requests(
    annotation: dict[str, Any],
    config: RuleExecutionConfig,
    api_client: RuleApiClient,
) -> tuple[list[dict[str, Any]], dict[tuple[str, str, str], dict[str, Any]]]:
    """Run planned requests and reconcile expected hypotheses by stable ID."""
    request_records = []
    score_index: dict[tuple[str, str, str], dict[str, Any]] = {}
    hypothesis_index = {
        (group["group_id"], hypothesis["hypothesis_id"]): hypothesis
        for group in annotation["groups"]
        for hypothesis in group["hypotheses"]
    }

    for plan in plan_rule_requests(annotation):
        request_id = _request_id(config, plan)
        expected_ids = set(plan["expected_hypotheses"])
        record: dict[str, Any] = {
            "request_id": request_id,
            "group_id": plan["group_id"],
            "polarity": plan["polarity"],
            "rule_target": plan["rule_target"],
            "expected_hypothesis_ids": sorted(expected_ids),
            "status": "SUCCESS",
            "error": None,
            "returned_hypothesis_count": 0,
            "recovered_expected_hypothesis_ids": [],
            "missing_expected_hypothesis_ids": [],
            "unlinked_api_scores": [],
        }
        try:
            returned = api_client.score(plan["group"], plan["rule_target"])
        except RuleApiError as error:
            record["status"] = "API_ERROR"
            record["error"] = str(error)
            returned = []

        returned_by_id: dict[str, dict[str, Any]] = {}
        for result in returned:
            hypothesis_id = result["hypothesis_id"]
            if hypothesis_id in returned_by_id:
                record["status"] = "RECONCILIATION_ERROR"
                record["error"] = f"Duplicate API hypothesis ID {hypothesis_id}"
                continue
            returned_by_id[hypothesis_id] = result
        record["returned_hypothesis_count"] = len(returned_by_id)

        for hypothesis_id in sorted(expected_ids):
            key = (plan["group_id"], hypothesis_id, plan["rule_target"])
            expected = hypothesis_index[(plan["group_id"], hypothesis_id)]
            result = returned_by_id.get(hypothesis_id)
            score_record = {
                "request_id": request_id,
                "group_id": plan["group_id"],
                "polarity": plan["polarity"],
                "hypothesis_id": hypothesis_id,
                "neutral_mass": expected["neutral_mass"],
                "rule_target": plan["rule_target"],
                "candidate_match_count": plan["expected_hypotheses"][hypothesis_id],
                "execution_status": "MISSING_EXPECTED_HYPOTHESIS",
                "score": None,
                "normalized_score": None,
                "has_rule_evidence": False,
                "evidence_status": None,
                "applied_presence": None,
                "applied_intensity": None,
                "descr_correct": "",
                "descr_incorrect": "",
            }
            if result is not None:
                if abs(result["neutral_mass"] - expected["neutral_mass"]) > 1e-9:
                    score_record["execution_status"] = "NEUTRAL_MASS_MISMATCH"
                    record["status"] = "RECONCILIATION_ERROR"
                else:
                    score_record.update(
                        {
                            "execution_status": "SCORED",
                            "score": result["score"],
                            "normalized_score": result["normalized_score"],
                            "has_rule_evidence": result["has_rule_evidence"],
                            "evidence_status": result["evidence_status"],
                            "applied_presence": result["applied_presence"],
                            "applied_intensity": result["applied_intensity"],
                            "descr_correct": result["descr_correct"],
                            "descr_incorrect": result["descr_incorrect"],
                        }
                    )
                    record["recovered_expected_hypothesis_ids"].append(hypothesis_id)
            if score_record["execution_status"] != "SCORED":
                record["missing_expected_hypothesis_ids"].append(hypothesis_id)
                if record["status"] == "SUCCESS":
                    record["status"] = "RECONCILIATION_ERROR"
            score_index[key] = score_record

        for hypothesis_id, result in returned_by_id.items():
            if hypothesis_id not in expected_ids:
                record["unlinked_api_scores"].append(
                    {
                        "hypothesis_id": hypothesis_id,
                        "neutral_mass": result["neutral_mass"],
                        "score": result["score"],
                        "normalized_score": result["normalized_score"],
                        "has_rule_evidence": result["has_rule_evidence"],
                        "evidence_status": result["evidence_status"],
                        "applied_presence": result["applied_presence"],
                        "applied_intensity": result["applied_intensity"],
                    }
                )
        request_records.append(record)
    return request_records, score_index


def attach_rule_scores(
    annotation: dict[str, Any],
    score_index: dict[tuple[str, str, str], dict[str, Any]],
) -> None:
    """Attach one score record per supported target to each hypothesis."""
    for group in annotation["groups"]:
        for hypothesis in group["hypotheses"]:
            targets = sorted(
                {
                    match["rule_target"]
                    for match in hypothesis["compound_matches"]
                    if match.get("rule_target")
                }
            )
            hypothesis["rule_scores"] = [
                score_index[(group["group_id"], hypothesis["hypothesis_id"], target)]
                for target in targets
            ]


def _score_summary(
    annotation: dict[str, Any],
    requests: list[dict[str, Any]],
    scores: list[dict[str, Any]],
) -> dict[str, Any]:
    scored = [item for item in scores if item["execution_status"] == "SCORED"]
    values = [item["score"] for item in scored]
    normalized_values = [
        item["normalized_score"]
        for item in scored
        if item["normalized_score"] is not None
    ]
    targets = sorted({item["rule_target"] for item in scores})
    compound_links = [
        (group["group_id"], hypothesis["hypothesis_id"], match["rule_target"])
        for group in annotation["groups"]
        for hypothesis in group["hypotheses"]
        for match in hypothesis["compound_matches"]
        if match.get("rule_target")
    ]
    scored_keys = {
        (item["group_id"], item["hypothesis_id"], item["rule_target"])
        for item in scored
    }
    return {
        "planned_requests": len(requests),
        "successful_requests": sum(item["status"] == "SUCCESS" for item in requests),
        "api_error_requests": sum(item["status"] == "API_ERROR" for item in requests),
        "reconciliation_error_requests": sum(
            item["status"] == "RECONCILIATION_ERROR" for item in requests
        ),
        "expected_hypothesis_target_scores": len(scores),
        "recovered_hypothesis_target_scores": len(scored),
        "missing_hypothesis_target_scores": len(scores) - len(scored),
        "unlinked_api_scores": sum(len(item["unlinked_api_scores"]) for item in requests),
        "compound_links_with_supported_rule_target": len(compound_links),
        "compound_links_with_rule_scores": sum(key in scored_keys for key in compound_links),
        "score_sign_counts": {
            "positive": sum(value > 0 for value in values),
            "zero": sum(value == 0 for value in values),
            "negative": sum(value < 0 for value in values),
        },
        "score_min": min(values) if values else None,
        "score_max": max(values) if values else None,
        "score_mean": statistics.fmean(values) if values else None,
        "normalized_score_evidence_counts": {
            "positive": sum(value > 0 for value in normalized_values),
            "zero_balanced": sum(value == 0 for value in normalized_values),
            "negative": sum(value < 0 for value in normalized_values),
            "no_evidence": sum(item["normalized_score"] is None for item in scored),
        },
        "evidence_status_counts": {
            status: sum(item["evidence_status"] == status for item in scored)
            for status in (
                "SUPPORTING", "BALANCED", "CONTRADICTING", "NO_EVIDENCE"
            )
        },
        "normalized_score_min": min(normalized_values) if normalized_values else None,
        "normalized_score_max": max(normalized_values) if normalized_values else None,
        "normalized_score_mean": (
            statistics.fmean(normalized_values) if normalized_values else None
        ),
        "applied_presence_total": sum(item["applied_presence"] for item in scored),
        "applied_intensity_total": sum(item["applied_intensity"] for item in scored),
        "per_rule_target": {
            target: _target_summary(requests, scores, target) for target in targets
        },
    }


def _target_summary(
    requests: list[dict[str, Any]], scores: list[dict[str, Any]], target: str
) -> dict[str, Any]:
    selected_requests = [item for item in requests if item["rule_target"] == target]
    selected_scores = [
        item
        for item in scores
        if item["rule_target"] == target and item["execution_status"] == "SCORED"
    ]
    values = [item["score"] for item in selected_scores]
    normalized_values = [
        item["normalized_score"]
        for item in selected_scores
        if item["normalized_score"] is not None
    ]
    return {
        "requests": len(selected_requests),
        "scores": len(selected_scores),
        "positive": sum(value > 0 for value in values),
        "zero": sum(value == 0 for value in values),
        "negative": sum(value < 0 for value in values),
        "score_min": min(values) if values else None,
        "score_max": max(values) if values else None,
        "score_mean": statistics.fmean(values) if values else None,
        "normalized_scores_with_evidence": len(normalized_values),
        "no_evidence": len(selected_scores) - len(normalized_values),
        "normalized_score_min": min(normalized_values) if normalized_values else None,
        "normalized_score_max": max(normalized_values) if normalized_values else None,
        "normalized_score_mean": (
            statistics.fmean(normalized_values) if normalized_values else None
        ),
    }


def _final_csv_rows(
    groups: list[dict[str, Any]],
    score_index: dict[tuple[str, str, str], dict[str, Any]],
) -> list[dict[str, Any]]:
    rows = annotation_csv_rows(groups)
    for row in rows:
        target = row.get("rule_target")
        hypothesis_id = row.get("hypothesis_id")
        if target and hypothesis_id:
            score = score_index[(row["group_id"], hypothesis_id, target)]
            row.update(
                {
                    "rule_request_id": score["request_id"],
                    "rule_execution_status": score["execution_status"],
                    "rule_score": score["score"],
                    "rule_normalized_score": score["normalized_score"],
                    "rule_has_evidence": score["has_rule_evidence"],
                    "rule_evidence_status": score["evidence_status"],
                    "rule_applied_presence": score["applied_presence"],
                    "rule_applied_intensity": score["applied_intensity"],
                    "rule_descr_correct": score["descr_correct"],
                    "rule_descr_incorrect": score["descr_incorrect"],
                }
            )
        elif row.get("compound_id"):
            row["rule_execution_status"] = "NO_SUPPORTED_RULE_TARGET"
        else:
            row["rule_execution_status"] = "NOT_APPLICABLE"
    return rows


def run_rule_execution(
    config: RuleExecutionConfig,
) -> tuple[Path, Path, Path, dict[str, Any]]:
    """Score enriched annotations and write final nested and flat artifacts."""
    annotation = json.loads(config.input_json.read_text(encoding="utf-8"))
    if annotation.get("dataset_id") != config.dataset_id:
        raise ValueError("Annotation dataset_id does not match rule config")
    if annotation.get("stage") != "adduct_annotation_and_compound_mass_search":
        raise ValueError("Rule input is not an enriched annotation artifact")

    requests, score_index = execute_rule_requests(
        annotation, config, RuleApiClient(config)
    )
    attach_rule_scores(annotation, score_index)
    scores = sorted(score_index.values(), key=lambda item: (
        item["group_id"], item["rule_target"], item["hypothesis_id"]
    ))
    summary = _score_summary(annotation, requests, scores)
    rule_hashes = {
        path.name: sha256(path)
        for path in sorted(config.rule_resources_dir.glob("*.xlsx"))
    }
    if not rule_hashes:
        raise ValueError(f"No rule workbooks found in {config.rule_resources_dir}")

    payload = {
        "schema_version": "1.0",
        "dataset_id": config.dataset_id,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "stage": "final_annotation_and_rule_scoring",
        "annotation_performed": True,
        "compound_mass_search_performed": True,
        "rule_execution_performed": True,
        "ground_truth_evaluation_performed": False,
        "input": {"path": str(config.input_json), "sha256": sha256(config.input_json)},
        "parameters": {
            "rule_api": {
                "endpoint": f"{config.api.base_url.rstrip('/')}/api/rule-puntuation",
                "tolerance_mode": config.tolerance_mode,
                "tolerance": config.tolerance,
                "mobile_phases": list(config.mobile_phases),
                "sample_type": config.sample_type,
                "max_retries": config.api.max_retries,
            },
            "rule_workbook_sha256": rule_hashes,
        },
        "summary": summary,
        "rule_requests": requests,
        "groups": annotation["groups"],
    }

    config.output_dir.mkdir(parents=True, exist_ok=True)
    json_path = config.output_dir / "final_annotations_with_scores.json"
    csv_path = config.output_dir / "final_annotations_with_scores.csv"
    scores_path = config.output_dir / "rule_scores.csv"
    json_path.write_text(json.dumps(payload, indent=2, allow_nan=False), encoding="utf-8")
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=ANNOTATION_CSV_FIELDS + FINAL_SCORE_FIELDS,
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(_final_csv_rows(annotation["groups"], score_index))
    with scores_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=RULE_SCORE_FIELDS)
        writer.writeheader()
        writer.writerows(scores)
    return json_path, csv_path, scores_path, summary

"""Evaluate scored lipid candidates against a reference XLSX workbook."""

from __future__ import annotations

import hashlib
import json
import math
import re
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

from lipid_rule_eval.annotation.lipid_type import parse_lipid_type


CHAIN_PATTERN = re.compile(r"(?<![A-Za-z0-9])(?:[dmt]|[OP]-)?(\d{1,3}):(\d{1,2})", re.I)
GOLD_HEADER_ROW = 4
GOLD_DATA_START_ROW = 5
POSITIVE_RT_COLUMN = 6
NEGATIVE_RT_COLUMN = 7
FIRST_ADDUCT_COLUMN = 10
LAST_ADDUCT_COLUMN = 25
FEDOROVA_RT_COLUMNS = {"HILIC": 13, "C18 RPC": 14, "C30 RPC": 15, "C30* RPC": 16}
# Neutral-mass offsets and labels from the Java adduct catalog. The S1 workbook
# marks observed ion species with text rather than supplying numerical ion m/z.
FEDOROVA_ADDUCTS = {
    8: ("[M+H]+", 1.007276),
    9: ("[M+H-H2O]+", -17.003200),
    10: ("[M+NH4]+", 18.033823),
    11: ("[M+HCOO]-", 44.998201),
    12: ("[M-H]-", -1.007276),
}


def sum_composition(value: str | None) -> tuple[int, int] | None:
    """Sum chain carbon and double-bond counts in one lipid name alternative."""
    if not value:
        return None
    chains = [(int(carbons), int(double_bonds)) for carbons, double_bonds in CHAIN_PATTERN.findall(value)]
    if not chains:
        return None
    return sum(item[0] for item in chains), sum(item[1] for item in chains)


def _integer(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return int(number) if number.is_integer() else None


def candidate_sum_key(match: dict[str, Any]) -> str | None:
    """Return a class-aware sum-composition key for one CMM match."""
    target = match.get("rule_target")
    if not target:
        return None
    carbons = _integer(match.get("cmm_number_carbons"))
    double_bonds = _integer(match.get("cmm_double_bonds"))
    if carbons is None or double_bonds is None:
        composition = sum_composition(match.get("compound_name"))
        if composition is None:
            return None
        carbons, double_bonds = composition
    return f"{target}({carbons}:{double_bonds})"


def _gold_target(name: str) -> str | None:
    parsed = parse_lipid_type(name)
    return parsed.rule_target if parsed else None


def _numeric(value: Any) -> float | None:
    return float(value) if isinstance(value, (int, float)) and math.isfinite(value) else None


def _fedorova_name(value: str) -> str:
    """Convert S1 space-separated shorthand to the shared class parser format."""
    name = value.strip()
    if "(" not in name:
        parts = name.split(maxsplit=1)
        if len(parts) == 2:
            name = f"{parts[0]}({parts[1]})"
    return name


def _read_fedorova_gold_standard(sheet: Any, method: str) -> list[dict[str, Any]]:
    """Read S1 using one acquisition method's RTs and its marked ion species.

    Reported Exact_mass values are used as supplied; no formula-based mass
    correction or RT substitution between chromatography methods is performed.
    """
    records = []
    rt_column = FEDOROVA_RT_COLUMNS[method]
    for row_number, values in enumerate(sheet.iter_rows(min_row=5, values_only=True), 5):
        neutral_mass = _numeric(values[6])
        rt_minutes = _numeric(values[rt_column - 1])
        if neutral_mass is None or neutral_mass <= 0 or rt_minutes is None:
            continue
        # LipidLynxX species notation is the explicitly normalized sum notation.
        sum_name = str(values[3] or values[1] or "").strip()
        name = _fedorova_name(sum_name)
        target = _gold_target(name)
        composition = sum_composition(name)
        if not target or composition is None:
            continue
        for polarity in ("POSITIVE", "NEGATIVE"):
            sign = "+" if polarity == "POSITIVE" else "-"
            adduct_masses = {
                adduct: neutral_mass + offset
                for column, (adduct, offset) in FEDOROVA_ADDUCTS.items()
                if values[column - 1] and adduct.endswith(sign)
            }
            if not adduct_masses:
                continue
            records.append(
                {
                    "gold_id": f"FED-{row_number:04d}-{polarity[:3]}",
                    "source_row": row_number,
                    "polarity": polarity,
                    "lipid_subclass": values[0],
                    "sum_name": sum_name,
                    "synonym_name": values[2],
                    "shorthand_name": values[4],
                    "sum_key": f"{target}({composition[0]}:{composition[1]})",
                    "rule_target": target,
                    "theoretical_neutral_mass": neutral_mass,
                    "rt_minutes": rt_minutes,
                    "rt_seconds": rt_minutes * 60.0,
                    "adduct_masses": adduct_masses,
                    "reference_method": method,
                    "mz_source": "S1 Exact_mass + Java-catalog adduct offset",
                }
            )
    return records


def read_gold_standard(
    path: Path, gold_format: str = "auto", reference_method: str | None = None
) -> list[dict[str, Any]]:
    """Read Martinez Table S2 or Fedorova S1 into the common reference schema.

    Fedorova requires an explicit chromatography method because S1 provides
    different RT columns for HILIC, C18 RPC, C30 RPC and C30* RPC.
    """
    if gold_format not in {"auto", "martinez", "fedorova"}:
        raise ValueError("gold_format must be auto, martinez or fedorova")
    workbook = load_workbook(path, read_only=True, data_only=True)
    try:
        if gold_format == "auto":
            if "Table S2" in workbook.sheetnames:
                gold_format = "martinez"
            elif "S1" in workbook.sheetnames:
                gold_format = "fedorova"
            else:
                raise ValueError("Unsupported gold-standard workbook: expected Table S2 or S1")
        if gold_format == "fedorova":
            if "S1" not in workbook.sheetnames:
                raise ValueError("Fedorova gold-standard workbook has no 'S1' sheet")
            if reference_method not in FEDOROVA_RT_COLUMNS:
                raise ValueError(
                    "Fedorova requires --reference-method: HILIC, C18 RPC, "
                    "C30 RPC or C30* RPC (choose the acquisition method)"
                )
            records = _read_fedorova_gold_standard(workbook["S1"], reference_method)
        else:
            if reference_method is not None:
                raise ValueError("--reference-method is only used for Fedorova S1")
            records = _read_martinez_gold_standard(workbook)
        if not records:
            raise ValueError("No usable references found for the selected gold standard and method")
        return records
    finally:
        workbook.close()


def _read_martinez_gold_standard(workbook: Any) -> list[dict[str, Any]]:
    """Normalize Table S2 rows and expand O/P alternatives and polarities."""
    if "Table S2" not in workbook.sheetnames:
        raise ValueError("Gold-standard workbook has no 'Table S2' sheet")
    sheet = workbook["Table S2"]
    headers = [sheet.cell(GOLD_HEADER_ROW, column).value for column in range(1, sheet.max_column + 1)]
    records: list[dict[str, Any]] = []

    for row_number, values in enumerate(
        sheet.iter_rows(min_row=GOLD_DATA_START_ROW, values_only=True),
        start=GOLD_DATA_START_ROW,
    ):
        sum_name = values[1]
        if not sum_name:
            continue
        for alternative_number, alternative in enumerate(str(sum_name).split("//"), start=1):
            alternative = alternative.strip()
            target = _gold_target(alternative)
            composition = sum_composition(alternative)
            if not target or composition is None:
                continue
            sum_key = f"{target}({composition[0]}:{composition[1]})"
            for polarity, rt_column in (
                ("POSITIVE", POSITIVE_RT_COLUMN),
                ("NEGATIVE", NEGATIVE_RT_COLUMN),
            ):
                rt_minutes = _numeric(values[rt_column - 1])
                if rt_minutes is None:
                    continue
                adduct_masses = {
                    str(headers[column - 1]).strip(): float(values[column - 1])
                    for column in range(FIRST_ADDUCT_COLUMN, LAST_ADDUCT_COLUMN + 1)
                    if _numeric(values[column - 1]) is not None
                }
                if not adduct_masses:
                    continue
                records.append(
                    {
                        "gold_id": f"GS-{row_number:04d}-{alternative_number}-{polarity[:3]}",
                        "source_row": row_number,
                        "polarity": polarity,
                        "lipid_subclass": values[0],
                        "sum_name": alternative,
                        "synonym_name": values[2],
                        "shorthand_name": values[3],
                        "sum_key": sum_key,
                        "rule_target": target,
                        "theoretical_neutral_mass": _numeric(values[4]),
                        "rt_minutes": rt_minutes,
                        "rt_seconds": rt_minutes * 60.0,
                        "adduct_masses": adduct_masses,
                    }
                )
    return records


def _candidate_id(group_id: str, hypothesis_id: str, sum_key: str) -> str:
    identity = f"{group_id}|{hypothesis_id}|{sum_key}"
    return "candidate-" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:20]


def build_candidates(results: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """Collapse CMM structural matches to one sum annotation per hypothesis."""
    candidates: list[dict[str, Any]] = []
    diagnostics = {
        "raw_compound_links": 0,
        "links_without_rule_target": 0,
        "links_without_sum_composition": 0,
    }
    for group in results["groups"]:
        for hypothesis in group["hypotheses"]:
            scores = {item["rule_target"]: item for item in hypothesis.get("rule_scores", [])}
            buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
            for match in hypothesis.get("compound_matches", []):
                diagnostics["raw_compound_links"] += 1
                if not match.get("rule_target"):
                    diagnostics["links_without_rule_target"] += 1
                    continue
                key = candidate_sum_key(match)
                if key is None:
                    diagnostics["links_without_sum_composition"] += 1
                    continue
                buckets[key].append(match)

            for sum_key, matches in sorted(buckets.items()):
                target = matches[0]["rule_target"]
                score = scores.get(target)
                compound_ids = sorted(
                    {
                        item.get("compound_id")
                        for item in matches
                        if item.get("compound_id") is not None
                    }
                )
                compound_names = sorted(
                    {item.get("compound_name", "") for item in matches}
                )
                candidates.append(
                    {
                        "candidate_id": _candidate_id(
                            group["group_id"], hypothesis["hypothesis_id"], sum_key
                        ),
                        "group_id": group["group_id"],
                        "source_files": ";".join(group.get("source_files", [])),
                        "polarity": group["polarity"],
                        "hypothesis_id": hypothesis["hypothesis_id"],
                        "neutral_mass": hypothesis["neutral_mass"],
                        "sum_key": sum_key,
                        "rule_target": target,
                        "compound_match_count": len(matches),
                        "compound_id_examples": ";".join(
                            str(value) for value in compound_ids[:25]
                        ),
                        "compound_name_examples": "; ".join(compound_names[:10]),
                        "compound_examples_omitted": max(
                            0, len(compound_names) - 10
                        ),
                        "items": hypothesis["items"],
                        "execution_status": score.get("execution_status") if score else "NO_SCORE",
                        "raw_score": score.get("score") if score else None,
                        "normalized_score": score.get("normalized_score") if score else None,
                        "has_evidence": score.get("has_rule_evidence", False) if score else False,
                        "evidence_status": score.get("evidence_status") if score else None,
                        "applied_presence": score.get("applied_presence") if score else None,
                        "applied_intensity": score.get("applied_intensity") if score else None,
                        "reference_supported": False,
                        "matched_gold_ids": [],
                        "matched_gold_names": [],
                        "best_mz_error_da": None,
                        "best_mz_error_ppm": None,
                        "best_rt_error_seconds": None,
                        "matched_adduct": None,
                        "top1": False,
                        "average_rank": None,
                        "retained": True,
                    }
                )
    diagnostics["sum_composition_candidates"] = len(candidates)
    diagnostics["collapsed_compound_links"] = (
        diagnostics["raw_compound_links"]
        - diagnostics["links_without_rule_target"]
        - diagnostics["links_without_sum_composition"]
        - len(candidates)
    )
    return candidates, diagnostics


def match_references(
    candidates: list[dict[str, Any]],
    references: list[dict[str, Any]],
    mz_tolerance_ppm: float,
    rt_tolerance_seconds: float,
) -> list[dict[str, Any]]:
    """Match ion m/z within ppm relative to reference m/z and RT in seconds."""
    if not math.isfinite(mz_tolerance_ppm) or mz_tolerance_ppm <= 0:
        raise ValueError("m/z tolerance in ppm must be finite and positive")
    if not math.isfinite(rt_tolerance_seconds) or rt_tolerance_seconds < 0:
        raise ValueError("RT tolerance in seconds must be finite and non-negative")
    reference_index: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for reference in references:
        reference_index[(reference["polarity"], reference["sum_key"])].append(reference)

    details: list[dict[str, Any]] = []
    for candidate in candidates:
        matches = []
        for reference in reference_index[(candidate["polarity"], candidate["sum_key"])]:
            for item in candidate["items"]:
                adduct = item.get("adduct_name")
                reference_mz = reference["adduct_masses"].get(adduct)
                if reference_mz is None or not math.isfinite(reference_mz) or reference_mz <= 0:
                    continue
                mz_error = abs(float(item["mz"]) - reference_mz)
                mz_error_ppm = mz_error / reference_mz * 1_000_000.0
                rt_error = abs(float(item["rt_seconds"]) - reference["rt_seconds"])
                within_mz = mz_error_ppm <= mz_tolerance_ppm or math.isclose(
                    mz_error_ppm, mz_tolerance_ppm, rel_tol=0.0, abs_tol=1e-9
                )
                if within_mz and rt_error <= rt_tolerance_seconds:
                    matches.append((mz_error, rt_error, adduct, item, reference, reference_mz))
        if not matches:
            continue
        matches.sort(key=lambda item: (item[0], item[1], item[4]["gold_id"]))
        best = matches[0]
        candidate.update(
            {
                "reference_supported": True,
                "matched_gold_ids": sorted({item[4]["gold_id"] for item in matches}),
                "matched_gold_names": sorted({item[4]["sum_name"] for item in matches}),
                "best_mz_error_da": best[0],
                "best_mz_error_ppm": best[0] / best[5] * 1_000_000.0,
                "best_rt_error_seconds": best[1],
                "matched_adduct": best[2],
            }
        )
        for mz_error, rt_error, adduct, item, reference, reference_mz in matches:
            details.append(
                {
                    "candidate_id": candidate["candidate_id"],
                    "group_id": candidate["group_id"],
                    "hypothesis_id": candidate["hypothesis_id"],
                    "sum_key": candidate["sum_key"],
                    "gold_id": reference["gold_id"],
                    "gold_sum_name": reference["sum_name"],
                    "polarity": candidate["polarity"],
                    "adduct": adduct,
                    "observed_mz": item["mz"],
                    "reference_mz": reference_mz,
                    "mz_error_da": mz_error,
                    "mz_error_ppm": mz_error / reference_mz * 1_000_000.0,
                    "observed_rt_seconds": item["rt_seconds"],
                    "reference_rt_seconds": reference["rt_seconds"],
                    "rt_error_seconds": rt_error,
                }
            )
    return details


def rank_candidates(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Rank evidence-bearing candidates per group, retaining all maximum ties."""
    by_group: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for candidate in candidates:
        by_group[candidate["group_id"]].append(candidate)

    group_rows = []
    for group_id, group_candidates in sorted(by_group.items()):
        ranked = [item for item in group_candidates if item["normalized_score"] is not None]
        ranked.sort(key=lambda item: (-item["normalized_score"], item["candidate_id"]))
        position = 1
        index = 0
        while index < len(ranked):
            end = index + 1
            while end < len(ranked) and math.isclose(
                ranked[end]["normalized_score"], ranked[index]["normalized_score"],
                rel_tol=0.0, abs_tol=1e-12,
            ):
                end += 1
            average_rank = (position + (position + end - index - 1)) / 2.0
            for candidate in ranked[index:end]:
                candidate["average_rank"] = average_rank
                candidate["top1"] = index == 0
                candidate["retained"] = index == 0
            position += end - index
            index = end

        supported = [item for item in group_candidates if item["reference_supported"]]
        retained = [item for item in group_candidates if item["retained"]]
        retained_supported = [item for item in retained if item["reference_supported"]]
        unique_supported = supported[0] if len(supported) == 1 else None
        top1_eligible = bool(
            unique_supported and unique_supported["normalized_score"] is not None and ranked
        )
        group_rows.append(
            {
                "group_id": group_id,
                "polarity": group_candidates[0]["polarity"],
                "candidate_count_before": len(group_candidates),
                "candidate_count_with_evidence": len(ranked),
                "candidate_count_no_evidence": len(group_candidates) - len(ranked),
                "candidate_count_retained": len(retained),
                "candidate_count_removed": len(group_candidates) - len(retained),
                "reference_supported_before": len(supported),
                "reference_supported_retained": len(retained_supported),
                "candidate_set_reduced": len(retained) < len(group_candidates),
                "reduced_and_reference_preserved": bool(
                    len(retained) < len(group_candidates) and retained_supported
                ),
                "top_score": max((item["normalized_score"] for item in ranked), default=None),
                "top_tie_count": sum(item["top1"] for item in ranked),
                "single_reference_supported": len(supported) == 1,
                "top1_evaluable": top1_eligible,
                "reference_is_top1": unique_supported["top1"] if top1_eligible else None,
                "reference_average_rank": (
                    unique_supported["average_rank"] if top1_eligible else None
                ),
                "reference_reciprocal_rank": (
                    1.0 / unique_supported["average_rank"] if top1_eligible else None
                ),
            }
        )
    return group_rows


def _divide(numerator: int, denominator: int) -> float | None:
    return numerator / denominator if denominator else None


def metric_rows(
    candidates: list[dict[str, Any]],
    groups: list[dict[str, Any]],
    diagnostics: dict[str, int],
) -> list[dict[str, Any]]:
    """Calculate the manuscript metrics and transparent supporting counts."""
    supported = [item for item in candidates if item["reference_supported"]]
    unmatched = [item for item in candidates if not item["reference_supported"]]
    retained = [item for item in candidates if item["retained"]]
    supported_retained = [item for item in supported if item["retained"]]
    unmatched_removed = [item for item in unmatched if not item["retained"]]
    groups_with_reference = [item for item in groups if item["reference_supported_before"]]
    reduced_preserved = [item for item in groups if item["reduced_and_reference_preserved"]]
    top1_groups = [item for item in groups if item["top1_evaluable"]]
    top1_successes = [item for item in top1_groups if item["reference_is_top1"]]
    single_reference_groups = [item for item in groups if item["single_reference_supported"]]
    reciprocal_ranks = [item["reference_reciprocal_rank"] for item in top1_groups]
    evidence_candidates = [item for item in candidates if item["has_evidence"]]

    def metric(name: str, numerator: int | float, denominator: int | None, note: str) -> dict[str, Any]:
        return {
            "metric": name,
            "numerator": numerator,
            "denominator": denominator,
            "value": _divide(int(numerator), denominator) if denominator is not None else numerator,
            "note": note,
        }

    return [
        metric("Raw CMM compound links", diagnostics["raw_compound_links"], None,
               "Before lipid and sum-composition filtering."),
        metric("Sum-composition candidates", len(candidates), None,
               "Unique group + neutral-mass hypothesis + lipid sum composition."),
        metric("Feature groups with candidates", len(groups), None, "Groups entering evaluation."),
        metric("Reference-supported candidates", len(supported), len(candidates),
               "Same polarity, subclass/sum composition, adduct m/z and RT."),
        metric("Candidates retained", len(retained), len(candidates),
               "All maximum-score ties plus candidates without rule evidence."),
        metric("Candidate reduction rate", len(candidates) - len(retained), len(candidates),
               "Lower-scoring evidence-bearing candidates removed."),
        metric("Reference-supported retention", len(supported_retained), len(supported),
               "Manuscript reference-supported preservation metric."),
        metric("Reference-supported false-elimination rate", len(supported) - len(supported_retained), len(supported),
               "Complement of reference-supported retention."),
        metric("Reference-unmatched removal rate", len(unmatched_removed), len(unmatched),
               "Reported separately because unmatched is not proof of incorrectness."),
        metric("Reduced groups preserving a reference", len(reduced_preserved), len(groups_with_reference),
               "Groups reduced while retaining at least one supported candidate."),
        metric("Rule-evidence coverage", len(evidence_candidates), len(candidates),
               "Candidates with at least one non-zero rule contribution."),
        metric("Groups with reference-supported candidates", len(groups_with_reference), None,
               "Denominator for group-level preservation."),
        metric("Single-reference groups", len(single_reference_groups), None,
               "Groups eligible for ranking assessment before evidence exclusions."),
        metric("Top-1 accuracy (ties accepted)", len(top1_successes), len(top1_groups),
               "Single-reference groups; a tied maximum counts as top-1."),
        metric("Mean reciprocal rank", sum(reciprocal_ranks) / len(reciprocal_ranks) if reciprocal_ranks else 0.0,
               None, "Ties use average rank; groups without ranked reference are excluded."),
        metric("Top-1 evaluable groups", len(top1_groups), None,
               "Exactly one supported candidate and that candidate has rule evidence."),
        metric("Single-reference groups excluded from ranking", len(single_reference_groups) - len(top1_groups), None,
               "The supported candidate had no normalized evidence score."),
    ]


def _class_rows(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows = []
    for target in sorted({item["rule_target"] for item in candidates}):
        selected = [item for item in candidates if item["rule_target"] == target]
        supported = [item for item in selected if item["reference_supported"]]
        retained = [item for item in selected if item["retained"]]
        supported_retained = [item for item in supported if item["retained"]]
        rows.append(
            {
                "rule_target": target,
                "candidates": len(selected),
                "with_evidence": sum(item["has_evidence"] for item in selected),
                "evidence_coverage": _divide(sum(item["has_evidence"] for item in selected), len(selected)),
                "reference_supported": len(supported),
                "reference_supported_retained": len(supported_retained),
                "reference_retention": _divide(len(supported_retained), len(supported)),
                "retained": len(retained),
                "candidate_reduction": _divide(len(selected) - len(retained), len(selected)),
            }
        )
    return rows


def _flat_candidate(candidate: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in candidate.items()
        if key != "items"
    } | {
        "matched_gold_ids": ";".join(candidate["matched_gold_ids"]),
        "matched_gold_names": "; ".join(candidate["matched_gold_names"]),
    }


def _flat_reference(reference: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value for key, value in reference.items() if key != "adduct_masses"
    } | {
        "adduct_masses": "; ".join(
            f"{name}={mass:.6f}" for name, mass in reference["adduct_masses"].items()
        )
    }


def _write_table(sheet: Any, rows: Iterable[dict[str, Any]], headers: list[str]) -> None:
    header_fill = PatternFill("solid", fgColor="1F4E78")
    for column, header in enumerate(headers, start=1):
        cell = sheet.cell(1, column, header)
        cell.fill = header_fill
        cell.font = Font(color="FFFFFF", bold=True)
        cell.alignment = Alignment(wrap_text=True, vertical="top")
    for row_number, row in enumerate(rows, start=2):
        for column, header in enumerate(headers, start=1):
            value = row.get(header)
            sheet.cell(row_number, column, value)
    sheet.freeze_panes = "A2"
    sheet.auto_filter.ref = sheet.dimensions
    for column, header in enumerate(headers, start=1):
        values = [str(sheet.cell(row, column).value or "") for row in range(1, min(sheet.max_row, 500) + 1)]
        sheet.column_dimensions[get_column_letter(column)].width = min(45, max(10, max(map(len, values)) + 2))


def write_report(
    output_path: Path,
    metrics: list[dict[str, Any]],
    groups: list[dict[str, Any]],
    candidates: list[dict[str, Any]],
    match_details: list[dict[str, Any]],
    references: list[dict[str, Any]],
    parameters: list[dict[str, Any]],
) -> None:
    """Write a formatted, auditable multi-sheet XLSX evaluation report."""
    workbook = Workbook()
    workbook.remove(workbook.active)
    tables = [
        ("Summary", metrics),
        ("Group metrics", groups),
        ("Candidates", [_flat_candidate(item) for item in candidates]),
        ("Reference matches", match_details),
        ("Class metrics", _class_rows(candidates)),
        ("Gold standard", [_flat_reference(item) for item in references]),
        ("Parameters", parameters),
    ]
    for title, rows in tables:
        sheet = workbook.create_sheet(title)
        headers = list(rows[0]) if rows else ["message"]
        _write_table(sheet, rows, headers)
        if title == "Summary":
            for row in range(2, sheet.max_row + 1):
                if sheet.cell(row, 3).value is not None:
                    sheet.cell(row, 4).number_format = "0.00%"
        if title in {"Class metrics"}:
            for column in range(1, sheet.max_column + 1):
                if sheet.cell(1, column).value in {"evidence_coverage", "reference_retention", "candidate_reduction"}:
                    for row in range(2, sheet.max_row + 1):
                        sheet.cell(row, column).number_format = "0.00%"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output_path)


def evaluate(
    results_json: Path,
    gold_standard: Path,
    output_path: Path,
    mz_tolerance_ppm: float = 20.0,
    rt_tolerance_seconds: float = 6.0,
    gold_format: str = "auto",
    reference_method: str | None = None,
) -> dict[str, Any]:
    """Run reference matching, ranking and report generation."""
    results = json.loads(results_json.read_text(encoding="utf-8"))
    references = read_gold_standard(gold_standard, gold_format, reference_method)
    candidates, diagnostics = build_candidates(results)
    match_details = match_references(
        candidates, references, mz_tolerance_ppm, rt_tolerance_seconds
    )
    groups = rank_candidates(candidates)
    metrics = metric_rows(candidates, groups, diagnostics)
    parameters = [
        {"parameter": "created_at", "value": datetime.now(timezone.utc).isoformat()},
        {"parameter": "results_json", "value": str(results_json.resolve())},
        {"parameter": "gold_standard", "value": str(gold_standard.resolve())},
        {"parameter": "m/z tolerance (ppm)", "value": mz_tolerance_ppm},
        {"parameter": "m/z error definition", "value": "abs(observed_mz - reference_mz) / reference_mz * 1e6"},
        {"parameter": "gold-standard format", "value": "fedorova" if references[0].get("reference_method") else "martinez"},
        {"parameter": "reference chromatography method", "value": reference_method or "Table S2 polarity-specific RT"},
        {"parameter": "RT tolerance (seconds)", "value": rt_tolerance_seconds},
        {"parameter": "ranking score", "value": "normalized_score descending"},
        {"parameter": "top-1 tie policy", "value": "all maximum-score ties are top-1"},
        {"parameter": "MRR tie policy", "value": "average rank"},
        {"parameter": "no-evidence policy", "value": "retained, excluded from ranking metrics"},
        {"parameter": "candidate identity", "value": "group + hypothesis + subclass sum composition"},
        {"parameter": "reference matching", "value": "polarity + subclass/sum + labelled adduct m/z + RT"},
    ]
    write_report(
        output_path, metrics, groups, candidates, match_details, references, parameters
    )
    return {
        "output": str(output_path.resolve()),
        "diagnostics": diagnostics,
        "metrics": metrics,
        "groups": len(groups),
        "reference_records": len(references),
        "reference_match_details": len(match_details),
    }

"""HTTP client for the Java Drools scoring endpoint."""

from __future__ import annotations

import json
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from lipid_rule_eval.annotation.api_client import (
    AnnotationApiError,
    normalize_hypothesis_response,
)
from lipid_rule_eval.config import RuleExecutionConfig


class RuleApiError(RuntimeError):
    """Raised when a rule-scoring request cannot produce a valid response."""


class RuleApiClient:
    """Client for ``POST /api/rule-puntuation`` with bounded retries."""

    def __init__(self, config: RuleExecutionConfig):
        self.config = config
        self.endpoint = f"{config.api.base_url.rstrip('/')}/api/rule-puntuation"

    def score(self, group: dict[str, Any], rule_target: str) -> list[dict[str, Any]]:
        """Score all API annotation hypotheses for one group and lipid class."""
        payload = {
            "features": [
                {
                    "mzValue": feature["mz"],
                    "intensity": feature["intensity_height"],
                    "retentionTime": feature["rt_seconds"],
                }
                for feature in group["features"]
            ],
            "mobilePhases": list(self.config.mobile_phases),
            "toleranceMode": self.config.tolerance_mode,
            "tolerance": self.config.tolerance,
            "ruleTarget": rule_target,
            "ionizationMode": group["polarity"],
            "sampleType": self.config.sample_type,
        }
        body = self._post(payload, group["group_id"], rule_target)
        results = body.get("results") if isinstance(body, dict) else None
        if not isinstance(results, list):
            raise RuleApiError("Rule API response has no results array")

        normalized = []
        for result in results:
            if not isinstance(result, dict) or not isinstance(
                result.get("annotatedFeature"), dict
            ):
                raise RuleApiError("Rule API result has no annotatedFeature")
            try:
                hypothesis = normalize_hypothesis_response(
                    result["annotatedFeature"], group
                )
            except AnnotationApiError as error:
                raise RuleApiError(str(error)) from error
            for field in ("score", "appliedPresence", "appliedIntensity"):
                if not isinstance(result.get(field), int):
                    raise RuleApiError(f"Rule API result has no integer {field}")
            normalized_score = result.get("normalizedScore")
            has_evidence = result.get("hasEvidence")
            evidence_status = result.get("evidenceStatus")
            if not isinstance(has_evidence, bool):
                raise RuleApiError("Rule API result has no boolean hasEvidence")
            valid_statuses = {
                "NO_EVIDENCE", "SUPPORTING", "BALANCED", "CONTRADICTING"
            }
            if evidence_status not in valid_statuses:
                raise RuleApiError("Rule API result has no valid evidenceStatus")
            if normalized_score is None:
                if has_evidence or evidence_status != "NO_EVIDENCE":
                    raise RuleApiError("Null normalizedScore must mean NO_EVIDENCE")
                if (
                    result["score"] != 0
                    or result["appliedPresence"] != 0
                    or result["appliedIntensity"] != 0
                ):
                    raise RuleApiError("NO_EVIDENCE result contains rule contributions")
            elif isinstance(normalized_score, (int, float)) and not isinstance(
                normalized_score, bool
            ):
                normalized_score = float(normalized_score)
                if not -1.000000000001 <= normalized_score <= 1.000000000001:
                    raise RuleApiError("normalizedScore is outside [-1, 1]")
                expected_status = (
                    "SUPPORTING" if result["score"] > 0
                    else "CONTRADICTING" if result["score"] < 0
                    else "BALANCED"
                )
                if not has_evidence or evidence_status != expected_status:
                    raise RuleApiError("Evidence flags do not agree with the score")
            else:
                raise RuleApiError("Rule API result has no valid normalizedScore")
            hypothesis.update(
                {
                    "score": result["score"],
                    "normalized_score": normalized_score,
                    "has_rule_evidence": has_evidence,
                    "evidence_status": evidence_status,
                    "descr_correct": result.get("descrCorrect") or "",
                    "descr_incorrect": result.get("descrIncorrect") or "",
                    "applied_presence": result["appliedPresence"],
                    "applied_intensity": result["appliedIntensity"],
                }
            )
            normalized.append(hypothesis)
        return normalized

    def _post(
        self, payload: dict[str, Any], group_id: str, rule_target: str
    ) -> dict[str, Any]:
        encoded = json.dumps(payload).encode("utf-8")
        attempts = self.config.api.max_retries + 1
        for attempt in range(attempts):
            request = Request(
                self.endpoint,
                data=encoded,
                headers={"Accept": "application/json", "Content-Type": "application/json"},
                method="POST",
            )
            try:
                with urlopen(
                    request, timeout=self.config.api.timeout_seconds
                ) as response:
                    return json.load(response)
            except HTTPError as error:
                detail = error.read().decode("utf-8", errors="replace")
                if error.code < 500 or attempt == attempts - 1:
                    raise RuleApiError(
                        f"Rule API returned HTTP {error.code} for "
                        f"{group_id}/{rule_target}: {detail}"
                    ) from error
            except (URLError, TimeoutError) as error:
                if attempt == attempts - 1:
                    raise RuleApiError(
                        f"Cannot call rule API at {self.endpoint} for "
                        f"{group_id}/{rule_target}: {error}"
                    ) from error
            except (json.JSONDecodeError, UnicodeDecodeError) as error:
                raise RuleApiError("Rule API returned invalid JSON") from error
            time.sleep(0.25 * (attempt + 1))
        raise AssertionError("unreachable")

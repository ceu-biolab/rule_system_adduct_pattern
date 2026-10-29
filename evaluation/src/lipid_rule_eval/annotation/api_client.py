"""HTTP client for the Java adduct-pattern annotation endpoint."""

from __future__ import annotations

import json
from collections import defaultdict
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from lipid_rule_eval.config import AnnotationApiConfig


class AnnotationApiError(RuntimeError):
    """Raised when the Java annotation endpoint cannot produce a valid result."""


class AnnotationApiClient:
    """Small standard-library client for ``POST /api/annotate-feature``."""

    def __init__(self, config: AnnotationApiConfig):
        self.config = config
        self.endpoint = f"{config.base_url.rstrip('/')}/api/annotate-feature"

    def annotate(self, group: dict[str, Any]) -> list[dict[str, Any]]:
        """Annotate one correlated feature group and normalize the response."""
        request_features = [
            {
                "mzValue": feature["mz"],
                "intensity": feature["intensity_height"],
                "retentionTime": feature["rt_seconds"],
            }
            for feature in group["features"]
        ]
        payload = {
            "features": request_features,
            "ionizationMode": group["polarity"],
            "toleranceMode": self.config.tolerance_mode,
            "tolerance": self.config.tolerance,
        }
        request = Request(
            self.endpoint,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Accept": "application/json", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request, timeout=self.config.timeout_seconds) as response:
                body = json.load(response)
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise AnnotationApiError(
                f"Annotation API returned HTTP {error.code} for {group['group_id']}: {detail}"
            ) from error
        except (URLError, TimeoutError) as error:
            raise AnnotationApiError(
                f"Cannot call annotation API at {self.endpoint}: {error}"
            ) from error
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            raise AnnotationApiError("Annotation API returned invalid JSON") from error

        results = body.get("results") if isinstance(body, dict) else None
        if not isinstance(results, list):
            raise AnnotationApiError("Annotation API response has no results array")
        return [normalize_hypothesis_response(item, group) for item in results]


def normalize_hypothesis_response(
    hypothesis: dict[str, Any], group: dict[str, Any]
) -> dict[str, Any]:
    """Normalize an API hypothesis and restore preprocessing feature IDs."""
    hypothesis_id = hypothesis.get("hypothesisId")
    neutral_mass = hypothesis.get("neutralMass")
    items = hypothesis.get("items")
    if not isinstance(hypothesis_id, str) or not hypothesis_id:
        raise AnnotationApiError("Annotation hypothesis has no hypothesisId")
    if not isinstance(neutral_mass, (int, float)):
        raise AnnotationApiError(f"{hypothesis_id} has no numeric neutralMass")
    if not isinstance(items, list):
        raise AnnotationApiError(f"{hypothesis_id} has no items array")

    feature_ids: dict[tuple[float, float, float], list[str]] = defaultdict(list)
    for feature in group["features"]:
        key = (
            float(feature["mz"]),
            float(feature["intensity_height"]),
            float(feature["rt_seconds"]),
        )
        feature_ids[key].append(feature["feature_id"])

    normalized_items = []
    for item in items:
        key = (
            float(item["mzValue"]),
            float(item["intensity"]),
            float(item["retentionTime"]),
        )
        candidates = feature_ids.get(key, [])
        if not candidates:
            raise AnnotationApiError(
                f"{hypothesis_id} returned a signal not present in {group['group_id']}"
            )
        normalized_items.append(
            {
                "feature_id": candidates.pop(0),
                "mz": key[0],
                "intensity_height": key[1],
                "rt_seconds": key[2],
                "adduct_name": item.get("adductName"),
            }
        )
    return {
        "hypothesis_id": hypothesis_id,
        "neutral_mass": float(neutral_mass),
        "matched_signal_count": sum(
            item["adduct_name"] is not None for item in normalized_items
        ),
        "items": normalized_items,
    }

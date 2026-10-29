from pathlib import Path

import pytest

from lipid_rule_eval.config import RuleApiConfig, RuleExecutionConfig
from lipid_rule_eval.rules.api_client import RuleApiClient, RuleApiError
from lipid_rule_eval.rules.pipeline import (
    _score_summary,
    attach_rule_scores,
    execute_rule_requests,
    plan_rule_requests,
)


class FakeRuleApiClient:
    def __init__(self):
        self.calls = []

    def score(self, group, rule_target):
        self.calls.append((group["group_id"], rule_target))
        return [
            {
                "hypothesis_id": "hyp-pc",
                "neutral_mass": 200.0,
                "matched_signal_count": 2,
                "items": [],
                "score": 3,
                "normalized_score": 1.0,
                "has_rule_evidence": True,
                "evidence_status": "SUPPORTING",
                "descr_correct": "PC evidence",
                "descr_incorrect": "",
                "applied_presence": 1,
                "applied_intensity": 1,
            },
            {
                "hypothesis_id": "hyp-unlinked",
                "neutral_mass": 220.0,
                "matched_signal_count": 2,
                "items": [],
                "score": 0,
                "normalized_score": None,
                "has_rule_evidence": False,
                "evidence_status": "NO_EVIDENCE",
                "descr_correct": "",
                "descr_incorrect": "",
                "applied_presence": 0,
                "applied_intensity": 0,
            },
        ]


def _config():
    return RuleExecutionConfig(
        dataset_id="pilot",
        input_json=Path("input.json"),
        output_dir=Path("out"),
        rule_resources_dir=Path("rules"),
        api=RuleApiConfig(
            base_url="http://localhost:9090", timeout_seconds=1.0, max_retries=0
        ),
        mobile_phases=("NH4", "CH3OH"),
        sample_type="PLASMA",
        tolerance_mode="PPM",
        tolerance=10.0,
    )


def _annotation():
    feature = {
        "feature_id": "F1",
        "source_file": "pilot.mzML",
        "polarity": "POSITIVE",
        "mz": 201.0,
        "rt_seconds": 10.0,
        "intensity_height": 1000.0,
    }
    pc_match = {"compound_id": 1, "rule_target": "PC"}
    return {
        "groups": [
            {
                "group_id": "POS-G000001",
                "polarity": "POSITIVE",
                "group_size": 2,
                "features": [feature],
                "hypotheses": [
                    {
                        "hypothesis_id": "hyp-pc",
                        "neutral_mass": 200.0,
                        "compound_matches": [pc_match, pc_match | {"compound_id": 2}],
                    },
                    {
                        "hypothesis_id": "hyp-unlinked",
                        "neutral_mass": 220.0,
                        "compound_matches": [{"compound_id": 3, "rule_target": None}],
                    },
                ],
            }
        ]
    }


def test_plans_one_request_for_duplicate_compound_targets():
    plans = plan_rule_requests(_annotation())

    assert len(plans) == 1
    assert plans[0]["rule_target"] == "PC"
    assert plans[0]["expected_hypotheses"] == {"hyp-pc": 2}


def test_executes_expected_score_and_records_unlinked_api_hypothesis():
    annotation = _annotation()
    client = FakeRuleApiClient()

    requests, score_index = execute_rule_requests(annotation, _config(), client)
    attach_rule_scores(annotation, score_index)
    scores = list(score_index.values())
    summary = _score_summary(annotation, requests, scores)

    assert client.calls == [("POS-G000001", "PC")]
    assert requests[0]["status"] == "SUCCESS"
    assert len(requests[0]["unlinked_api_scores"]) == 1
    assert score_index[("POS-G000001", "hyp-pc", "PC")]["score"] == 3
    assert score_index[("POS-G000001", "hyp-pc", "PC")]["normalized_score"] == 1.0
    assert score_index[("POS-G000001", "hyp-pc", "PC")]["evidence_status"] == "SUPPORTING"
    assert annotation["groups"][0]["hypotheses"][0]["rule_scores"][0]["score"] == 3
    assert summary["planned_requests"] == 1
    assert summary["compound_links_with_rule_scores"] == 2


def _api_result(score, normalized_score, has_evidence, evidence_status, presence):
    return {
        "annotatedFeature": {
            "hypothesisId": "hyp-pc",
            "neutralMass": 200.0,
            "items": [
                {
                    "mzValue": 201.0,
                    "intensity": 1000.0,
                    "retentionTime": 10.0,
                    "adductName": "[M+H]+",
                }
            ],
        },
        "score": score,
        "normalizedScore": normalized_score,
        "hasEvidence": has_evidence,
        "evidenceStatus": evidence_status,
        "descrCorrect": "",
        "descrIncorrect": "",
        "appliedPresence": presence,
        "appliedIntensity": 0,
    }


def test_api_client_distinguishes_no_evidence_from_balanced_evidence():
    group = _annotation()["groups"][0]
    client = RuleApiClient(_config())

    client._post = lambda *_: {
        "results": [_api_result(0, None, False, "NO_EVIDENCE", 0)]
    }
    no_evidence = client.score(group, "PC")[0]
    assert no_evidence["normalized_score"] is None
    assert no_evidence["has_rule_evidence"] is False
    assert no_evidence["evidence_status"] == "NO_EVIDENCE"

    client._post = lambda *_: {
        "results": [_api_result(0, 0.0, True, "BALANCED", 2)]
    }
    balanced = client.score(group, "PC")[0]
    assert balanced["normalized_score"] == 0.0
    assert balanced["has_rule_evidence"] is True
    assert balanced["evidence_status"] == "BALANCED"


def test_api_client_rejects_inconsistent_evidence_flags():
    group = _annotation()["groups"][0]
    client = RuleApiClient(_config())
    client._post = lambda *_: {
        "results": [_api_result(0, None, True, "BALANCED", 2)]
    }

    with pytest.raises(RuleApiError, match="Null normalizedScore"):
        client.score(group, "PC")

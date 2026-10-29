from __future__ import annotations

from lipid_rule_eval.annotation.pipeline import _csv_rows, _summary, annotate_payload


class FakeApiClient:
    def annotate(self, group):
        return [
            {
                "hypothesis_id": "hyp-test",
                "neutral_mass": 200.0,
                "matched_signal_count": 2,
                "items": [
                    {
                        "feature_id": feature["feature_id"],
                        "mz": feature["mz"],
                        "intensity_height": feature["intensity_height"],
                        "rt_seconds": feature["rt_seconds"],
                        "adduct_name": "[M+H]+",
                    }
                    for feature in group["features"]
                ],
            }
        ]


class FakeRepository:
    def find_by_neutral_mass(self, neutral_mass):
        assert neutral_mass == 200.0
        return [
            {
                "compound_id": 7,
                "cas_id": None,
                "compound_name": "Candidate",
                "formula": "C10H16O4",
                "mass": 200.0,
                "mass_error_da": 0.0,
                "mass_error_ppm": 0.0,
                "charge_type": 0,
                "charge_number": 0,
                "formula_type": "CHNOPS",
                "compound_type": 1,
                "compound_status": 1,
                "formula_type_int": 0,
                "logp": None,
                "rt_pred": None,
                "cmm_lipid_type": "PC(",
                "cmm_lipid_type_rt": "PC",
                "cmm_lipid_type_fragmentation": "Ester",
                "cmm_num_chains": 2,
                "cmm_number_carbons": 10,
                "cmm_double_bonds": 0,
                "name_lipid_type": "PC",
                "lipid_type": "PC",
                "lipid_type_source": "CMM",
                "rule_target": "PC",
                "lipid_type_conflict": False,
            }
        ]


def _feature(feature_id, mz):
    return {
        "group_id": "POS-G000001",
        "group_size": 2,
        "is_correlated_group": True,
        "feature_id": feature_id,
        "source_file": "pilot.mzML",
        "polarity": "POSITIVE",
        "mz": mz,
        "rt_seconds": 10.0,
        "intensity_height": 1000.0,
    }


def test_annotation_skips_singletons_and_attaches_compounds():
    pair = [_feature("F1", 201.0), _feature("F2", 223.0)]
    singleton = _feature("F3", 300.0) | {"group_id": "POS-G000002", "group_size": 1}
    preprocessing = {
        "groups": [
            {
                "group_id": "POS-G000001",
                "polarity": "POSITIVE",
                "group_size": 2,
                "features": pair,
            },
            {
                "group_id": "POS-G000002",
                "polarity": "POSITIVE",
                "group_size": 1,
                "features": [singleton],
            },
        ]
    }

    groups = annotate_payload(preprocessing, FakeApiClient(), FakeRepository())

    assert groups[0]["status"] == "ANNOTATED"
    assert groups[0]["hypotheses"][0]["compound_matches"][0]["compound_id"] == 7
    assert groups[1]["status"] == "SKIPPED_SINGLETON"
    assert _summary(groups)["neutral_mass_hypotheses"] == 1
    assert _summary(groups)["rule_target_counts"] == {"PC": 1}
    rows = _csv_rows(groups)
    assert rows[0]["hypothesis_id"] == "hyp-test"
    assert rows[0]["compound_name"] == "Candidate"
    assert rows[0]["rule_target"] == "PC"
    assert rows[1]["status"] == "SKIPPED_SINGLETON"

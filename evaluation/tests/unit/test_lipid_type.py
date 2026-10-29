from lipid_rule_eval.annotation.lipid_type import enrich_compound_match, parse_lipid_type


def test_parses_base_lipid_type_without_chain_details():
    parsed = parse_lipid_type("PC(16:0/18:1(9Z))")

    assert parsed.lipid_type == "PC"
    assert parsed.rule_target == "PC"


def test_maps_ether_lipids_to_specific_rule_target():
    parsed = parse_lipid_type("LPE(P-18:0/20:4)")

    assert parsed.lipid_type == "LPE"
    assert parsed.rule_target == "LPE_OP"


def test_uses_cmm_before_name_and_reports_conflict():
    match = enrich_compound_match(
        {
            "cmm_lipid_type": "TG(",
            "compound_name": "DG(18:0/18:1)",
        }
    )

    assert match["lipid_type"] == "TG"
    assert match["lipid_type_source"] == "CMM"
    assert match["rule_target"] == "TG"
    assert match["lipid_type_conflict"] is True


def test_uses_name_when_cmm_classification_is_absent():
    match = enrich_compound_match(
        {"cmm_lipid_type": None, "compound_name": "Cer(d18:1/24:0)"}
    )

    assert match["lipid_type"] == "CER"
    assert match["lipid_type_source"] == "COMPOUND_NAME"
    assert match["rule_target"] == "CER"

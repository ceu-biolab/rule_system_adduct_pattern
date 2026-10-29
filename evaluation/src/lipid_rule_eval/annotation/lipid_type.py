"""Conservative lipid-family parsing and Java rule-target mapping."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any


TYPE_PATTERN = re.compile(r"^\s*([A-Za-z][A-Za-z0-9_-]*)\s*\(")
ETHER_PATTERN = re.compile(r"^\s*(L?P[CE])\s*\(\s*[OP]-", re.IGNORECASE)

TYPE_ALIASES = {
    "LYSOPC": "LPC",
    "LYSOPE": "LPE",
    "LYSOPI": "LPI",
    "CERAMIDE": "CER",
    "GLCCER": "HEXCER",
    "GALCER": "HEXCER",
    "GLCBETA-CER": "HEXCER",
    "GALBETA-CER": "HEXCER",
    "GLUCOSYLCERAMIDE": "HEXCER",
    "GALACTOSYLCERAMIDE": "HEXCER",
}

DIRECT_RULE_TARGETS = {
    "CAR", "CE", "CER", "DG", "FA", "HEXCER", "LPC", "LPE", "LPI",
    "PC", "PE", "PI", "SM", "TG",
}


@dataclass(frozen=True)
class ParsedLipidType:
    """Canonical lipid family and optional Java ``RuleTarget`` value."""

    lipid_type: str
    rule_target: str | None


def parse_lipid_type(value: str | None) -> ParsedLipidType | None:
    """Parse the leading lipid family without interpreting chain contents."""
    if not value:
        return None
    stripped = value.strip()
    upper = stripped.upper()
    if upper == "CHOLESTEROL" or upper.startswith("CHOLESTEROL("):
        return ParsedLipidType("CHOL", "CHOL")
    if upper.startswith("CHOLESTERYL "):
        return ParsedLipidType("CE", "CE")

    match = TYPE_PATTERN.match(stripped)
    if not match:
        return None
    token = TYPE_ALIASES.get(match.group(1).upper(), match.group(1).upper())
    if ETHER_PATTERN.match(stripped) and token in {"LPC", "LPE", "PC", "PE"}:
        return ParsedLipidType(token, f"{token}_OP")
    rule_target = token if token in DIRECT_RULE_TARGETS else None
    return ParsedLipidType(token, rule_target)


def enrich_compound_match(match: dict[str, Any]) -> dict[str, Any]:
    """Attach curated-first lipid type, fallback provenance, and rule target."""
    cmm = parse_lipid_type(match.get("cmm_lipid_type"))
    name = parse_lipid_type(match.get("compound_name"))
    selected = cmm or name
    match.update(
        {
            "name_lipid_type": name.lipid_type if name else None,
            "lipid_type": selected.lipid_type if selected else None,
            "lipid_type_source": "CMM" if cmm else ("COMPOUND_NAME" if name else None),
            "rule_target": selected.rule_target if selected else None,
            "lipid_type_conflict": bool(
                cmm and name and cmm.lipid_type != name.lipid_type
            ),
        }
    )
    return match

#!/usr/bin/env python3
"""
Parse all .drl files and generate AdductRules.drl.xlsx Drools decision table.

Drools Decision Table row structure after RuleTable keyword:
  Row 1: type row  (CONDITION / ACTION / NAME)
  Row 2: object declarations  (fact type, e.g. "ResultItem" or "$a1: ResultItem")
  Row 3: code snippets        (field constraint / action code with $1)
  Row 4: column labels        (human-readable, ignored by Drools)
  Row 5+: data rows
"""

import re
from pathlib import Path
import openpyxl
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter

DRL_BASE = Path("/mnt/Data/04-Invest/03-RuleSystemAdductPattern/src/main/resources/rules")
OUTPUT   = Path("/mnt/Data/04-Invest/03-RuleSystemAdductPattern/src/main/resources"
                "/rules/AdductRules.drl.xlsx")

# ── Regex patterns ──────────────────────────────────────────────────────────
RULE_RE      = re.compile(r'rule\s+"([^"]+)"\s*\nwhen(.*?)then(.*?)end', re.DOTALL)
PRESENCE_RE  = re.compile(r'\$adduct\s*:\s*ResultItem\s*\(\s*adductName\s*==\s*"([^"]+)"\s*\)')
ABSENCE_RE   = re.compile(r'not\s+ResultItem\s*\(\s*adductName\s*==\s*"([^"]+)"\s*\)')
ADDUCT1_RE   = re.compile(r'\$adduct1\s*:\s*ResultItem\s*\(\s*adductName\s*==\s*"([^"]+)"\s*\)')
ADDUCT2_RE   = re.compile(r'\$adduct2\s*:\s*ResultItem\s*\(\s*adductName\s*==\s*"([^"]+)"\s*\)')
EVAL_GT_RE   = re.compile(r'eval\(\s*\$adduct1\.getIntensity\(\)\s*>\s*\$adduct2\.getIntensity\(\)\s*\)')
PHASE_RE     = re.compile(r'eval\(\$phase\d+\s*==\s*MobilePhases\.(\w+)\)')
SCORE_RE     = re.compile(r'lipid\.setScore\((-?\d+)\)')
DESCR_OK_RE  = re.compile(r'lipid\.setDescrCorrect\("([^"]+)"\)')
DESCR_BAD_RE = re.compile(r'lipid\.setDescrIncorrect\("([^"]+)"\)')


# ── DRL parsing ─────────────────────────────────────────────────────────────
def parse_drl(path: Path) -> list:
    text = path.read_text(encoding='utf-8')
    rules = []
    for m in RULE_RE.finditer(text):
        name, when_blk, then_blk = m.group(1), m.group(2), m.group(3)
        if 'Dummy' in name:
            continue

        a1   = ADDUCT1_RE.search(when_blk)
        a2   = ADDUCT2_RE.search(when_blk)
        pres = PRESENCE_RE.search(when_blk)
        abs_ = ABSENCE_RE.search(when_blk)
        phases = PHASE_RE.findall(when_blk)

        sm = SCORE_RE.search(then_blk)
        score = int(sm.group(1)) if sm else 0
        dm  = DESCR_OK_RE.search(then_blk)
        dc  = dm.group(1) if dm else ''
        dm2 = DESCR_BAD_RE.search(then_blk)
        di  = dm2.group(1) if dm2 else ''

        if a1 and a2:
            gt = bool(EVAL_GT_RE.search(when_blk))
            rules.append({'name': name,
                          'type': 'intensity_gt' if gt else 'intensity_lt',
                          'adduct1': a1.group(1), 'adduct2': a2.group(1),
                          'phases': phases, 'score': score,
                          'descr_correct': dc, 'descr_incorrect': di})
        elif pres:
            rules.append({'name': name, 'type': 'presence',
                          'adduct': pres.group(1),
                          'phases': phases, 'score': score,
                          'descr_correct': dc, 'descr_incorrect': ''})
        elif abs_:
            rules.append({'name': name, 'type': 'absence',
                          'adduct': abs_.group(1),
                          'phases': phases, 'score': score,
                          'descr_correct': '', 'descr_incorrect': di})
    return rules


def parse_all(base: Path) -> list:
    rules = []
    for f in sorted(base.rglob('*.drl')):
        if 'userFiles' in str(f):
            continue
        rules.extend(parse_drl(f))
    return rules


# ── Styles ───────────────────────────────────────────────────────────────────
HDR_FILL   = PatternFill('solid', fgColor='4472C4')
COND_FILL  = PatternFill('solid', fgColor='E2EFDA')
ACT_FILL   = PatternFill('solid', fgColor='FFF2CC')
LBL_FILL   = PatternFill('solid', fgColor='D9E1F2')
WHITE_BOLD = Font(bold=True, color='FFFFFF')
BOLD       = Font(bold=True)


# ── Excel generation ─────────────────────────────────────────────────────────
def write_metadata(ws, start_row: int) -> int:
    r = start_row
    entries = [
        ('RuleSet',   'ceu.biolab.cmm.rulePuntuation.rules'),
        ('Import',    'ceu.biolab.cmm.shared.dto.FeatureAnnotation.AnnotatedFeature,'
                      'ceu.biolab.cmm.shared.dto.FeatureAnnotation.ResultItem,'
                      'ceu.biolab.cmm.shared.domain.MobilePhases,'
                      'java.util.List'),
        ('Variables', 'AnnotatedFeature lipid'),
        ('Variables', 'List mobilePhases'),
        ('Variables', 'String sampleType'),
    ]
    for key, val in entries:
        ws.cell(r, 2, key).font = BOLD
        ws.cell(r, 3, val)
        r += 1
    return r + 1


def write_table(ws, start_row: int, table_name: str,
                types: list,
                declarations: list,   # object type row  (one per column)
                snippets: list,       # code / constraint row (one per column)
                labels: list,
                data: list) -> int:
    """
    Each param list has one entry per column.  Columns start at openpyxl col 2.
    Col 1 (A) carries 'DESCRIPTION' in the type row only.
    """
    r = start_row

    # RuleTable header
    c = ws.cell(r, 2, f'RuleTable {table_name}')
    c.fill = HDR_FILL; c.font = WHITE_BOLD
    r += 1

    # Type row
    ws.cell(r, 1, 'DESCRIPTION').font = BOLD
    for ci, t in enumerate(types, start=2):
        cell = ws.cell(r, ci, t)
        cell.font = BOLD
        if t == 'CONDITION': cell.fill = COND_FILL
        elif t == 'ACTION':  cell.fill = ACT_FILL
    r += 1

    # Object declarations row
    for ci, decl in enumerate(declarations, start=2):
        if decl: ws.cell(r, ci, decl)
    r += 1

    # Code snippets row
    for ci, snip in enumerate(snippets, start=2):
        if snip: ws.cell(r, ci, snip)
    r += 1

    # Labels row
    for ci, lbl in enumerate(labels, start=2):
        if lbl:
            cell = ws.cell(r, ci, lbl)
            cell.fill = LBL_FILL
    r += 1

    # Data rows
    for row_vals in data:
        for ci, val in enumerate(row_vals, start=2):
            if val is not None and val != '':
                ws.cell(r, ci, val)
        r += 1

    return r + 1


# ── Table definitions ────────────────────────────────────────────────────────
# Each column is described as (type, declaration, snippet, label).
# For presence/absence:
#   presence COND: decl='ResultItem',      snip='adductName == "$1"'
#   absence  COND: decl='not ResultItem',  snip='adductName == "$1"'
#   phase    COND: decl='MobilePhases from mobilePhases',
#                  snip='this == MobilePhases.$1'
#   action   ACT:  decl='',               snip='lipid.setScore($1);'  etc.

def presence_table(rules: list):
    subset = [r for r in rules if r['type'] in ('presence', 'absence')]

    cols = [
        # (type, declaration, snippet, label)
        ('NAME',      '',                                   '',                                               'Rule Name'),
        ('CONDITION', 'ResultItem',                        'adductName == "$1"',                             'Adduct Present'),
        ('CONDITION', 'not ResultItem',                    'adductName == "$1"',                             'Adduct Absent'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',                        'Phase 1'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',                        'Phase 2'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',                        'Phase 3'),
        ('ACTION',    '',                                  'lipid.setScore($1);',                            'Score'),
        ('ACTION',    '',                                  'lipid.setDescrCorrect("$1");',                   'Descr Correct'),
        ('ACTION',    '',                                  'lipid.setDescrIncorrect("$1");',                 'Descr Incorrect'),
        ('ACTION',    '',                                  'lipid.setAppliedPresence(lipid.getAppliedPresence() + 1);', 'Applied Presence'),
    ]
    types        = [c[0] for c in cols]
    declarations = [c[1] for c in cols]
    snippets     = [c[2] for c in cols]
    labels       = [c[3] for c in cols]

    data = []
    for rule in subset:
        ph = (rule['phases'] + ['', '', ''])[:3]
        if rule['type'] == 'presence':
            data.append([rule['name'],
                         rule['adduct'], '',
                         ph[0], ph[1], ph[2],
                         rule['score'], rule['descr_correct'], '', 'x'])
        else:
            data.append([rule['name'],
                         '', rule['adduct'],
                         ph[0], ph[1], ph[2],
                         rule['score'], '', rule['descr_incorrect'], 'x'])

    return 'PresenceRules', types, declarations, snippets, labels, data


def intensity_table(rules: list, is_gt: bool):
    rule_type = 'intensity_gt' if is_gt else 'intensity_lt'
    subset    = [r for r in rules if r['type'] == rule_type]
    tag       = 'GT' if is_gt else 'LT'
    eval_snip = ('eval($a1.getIntensity() > $a2.getIntensity())'
                 if is_gt else
                 'eval($a1.getIntensity() < $a2.getIntensity())')

    cols = [
        ('NAME',      '',                                   '',           'Rule Name'),
        ('CONDITION', '$a1: ResultItem',                   'adductName == "$1"',  'Higher adduct ($a1)' if is_gt else 'Lower adduct ($a1)'),
        ('CONDITION', '$a2: ResultItem',                   'adductName == "$1"',  'Other adduct ($a2)'),
        # Standalone eval: blank declaration, full eval in snippet
        ('CONDITION', '',                                  eval_snip,    'Intensity eval'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',  'Phase 1'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',  'Phase 2'),
        ('CONDITION', 'MobilePhases from mobilePhases',   'this == MobilePhases.$1',  'Phase 3'),
        ('ACTION',    '',                                  'lipid.setScore($1);',                            'Score'),
        ('ACTION',    '',                                  'lipid.setDescrCorrect("$1");',                   'Descr Correct'),
        ('ACTION',    '',                                  'lipid.setDescrIncorrect("$1");',                 'Descr Incorrect'),
        ('ACTION',    '',                                  'lipid.setAppliedIntensity(lipid.getAppliedIntensity() + 1);', 'Applied Intensity'),
    ]
    types        = [c[0] for c in cols]
    declarations = [c[1] for c in cols]
    snippets     = [c[2] for c in cols]
    labels       = [c[3] for c in cols]

    data = []
    for rule in subset:
        ph = (rule['phases'] + ['', '', ''])[:3]
        data.append([rule['name'],
                     rule['adduct1'], rule['adduct2'], 'x',
                     ph[0], ph[1], ph[2],
                     rule['score'],
                     rule['descr_correct'],
                     rule['descr_incorrect'],
                     'x'])

    return f'IntensityRules{tag}', types, declarations, snippets, labels, data


# ── Main ──────────────────────────────────────────────────────────────────────
def generate(rules: list, out: Path):
    out.parent.mkdir(parents=True, exist_ok=True)

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = 'AdductRules'

    row = write_metadata(ws, 1)

    for table_fn, is_gt in [
        (presence_table, None),
        (intensity_table, True),
        (intensity_table, False),
    ]:
        if is_gt is None:
            args = table_fn(rules)
        else:
            args = table_fn(rules, is_gt)
        name, types, decls, snips, labels, data = args
        row = write_table(ws, row, name, types, decls, snips, labels, data)

    for col_cells in ws.columns:
        max_w = max((len(str(c.value)) for c in col_cells if c.value), default=8)
        ws.column_dimensions[get_column_letter(col_cells[0].column)].width = min(max_w + 2, 60)

    wb.save(out)

    by_type = {}
    for r in rules:
        by_type[r['type']] = by_type.get(r['type'], 0) + 1
    print(f"Saved: {out}")
    print(f"Total rules: {len(rules)}")
    for k, v in sorted(by_type.items()):
        print(f"  {k:15s}: {v}")


if __name__ == '__main__':
    rules = parse_all(DRL_BASE)
    # exclude the old puntuation_rules — we skip non-drl files already
    generate(rules, OUTPUT)

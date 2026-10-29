# Expert-system HTTP API

The application exposes three JSON/HTTP operations under `/api`. It listens on
port `9090` by default.

## Implementation locations

- `POST /api/annotate-feature`:
  `src/main/java/ceu/biolab/cmm/featureAnnotation/controller/FeatureAnnotationController.java`
- `POST /api/rule-puntuation`:
  `src/main/java/ceu/biolab/cmm/rulePuntuation/controller/RulePuntuationController.java`
- `POST /api/reload-rules`:
  `src/main/java/ceu/biolab/cmm/rulesReload/controller/RulesReloadController.java`
- Adduct-labelling implementation:
  `src/main/java/ceu/biolab/cmm/featureAnnotation/service/FeatureAnnotationService.java`
- Expert-system orchestration:
  `src/main/java/ceu/biolab/cmm/rulePuntuation/service/RulePuntuationService.java`
- Polarity-specific ion catalogs: `src/main/resources/adducts/`
- Drools decision tables: `src/main/resources/rules/`

## Intended service boundary

The recommended evaluation flow is:

```text
mzML
  -> pyOpenMS peak picking and mass-trace detection
  -> pyOpenMS deconvolution and feature grouping
  -> CMM mass search and lipid-class candidates
  -> one /api/rule-puntuation request per (feature group, candidate class)
  -> metrics
```

pyOpenMS owns deconvolution and grouping. Every API request must contain the
signals of **one** chromatographic feature group. The Java service owns:

1. charge inference from isotope spacing;
2. polarity-specific adduct and diagnostic-ion labelling;
3. annotation-hypothesis filtering and deduplication;
4. Drools scoring for a requested lipid class, when using `rule-puntuation`.

Retention time is preserved in responses and rule facts, but Java does not use
it to regroup the supplied signals.

## Ion-catalog semantics

Ordinary entries in `adducts_positive_mode.csv` and
`adducts_negative_mode.csv` store a signed offset from neutral molecular mass.
They may establish a neutral-mass hypothesis. Rule-specific formate, acetate,
sodium-acetate-cluster and `C2H7N2` labels are included using the exact strings
expected by Drools.

Fixed diagnostic ions live in `diagnostic_ions_positive_mode.csv`. They can be
assigned to a signal supporting an existing hypothesis but cannot establish a
neutral mass. In particular, `[C27H44]+` is retained because that is the
historical fact literal in the CE and cholesterol rules; it represents the
cholestadiene diagnostic signal at m/z 369.351578. The text
`[cholestadiene ion]+` appears only in rule names/descriptions and is not a fact
literal that the annotator must emit.

## Common feature representation

```json
{
  "mzValue": 201.007276,
  "intensity": 1000.0,
  "retentionTime": 120.0
}
```

- `mzValue`: measured mass-to-charge ratio.
- `intensity`: signal intensity used by intensity-order rules.
- `retentionTime`: retention time in the caller's unit; all signals in a request
  should use the same unit.

Mass tolerances use either `PPM` or `DALTON`. If `tolerance` is omitted, the
service uses 10 ppm or 0.1 Da, respectively.

## POST `/api/annotate-feature`

Assign adduct labels without firing expert-system rules. This endpoint is useful
for inspecting or evaluating the annotation stage independently.

Request:

```json
{
  "features": [
    {"mzValue": 201.007276, "intensity": 1000.0, "retentionTime": 120.0},
    {"mzValue": 222.989218, "intensity": 500.0, "retentionTime": 120.0}
  ],
  "ionizationMode": "POSITIVE",
  "toleranceMode": "PPM",
  "tolerance": 10.0
}
```

Response:

```json
{
  "results": [
    {
      "hypothesisId": "hyp-9ef91ba9-48f5-3db0-bf8c-3749f5d64ddc",
      "neutralMass": 200.0,
      "items": [
        {
          "mzValue": 201.007276,
          "intensity": 1000.0,
          "retentionTime": 120.0,
          "adductName": "[M+H]+"
        },
        {
          "mzValue": 222.989218,
          "intensity": 500.0,
          "retentionTime": 120.0,
          "adductName": "[M+Na]+"
        }
      ]
    }
  ]
}
```

The result may contain several neutral-mass/adduct hypotheses. `hypothesisId`
is deterministically derived from the inferred neutral mass and labelled input
signals, allowing clients to join the result to later CMM searches and scoring
operations. `neutralMass` is the neutral monoisotopic mass inferred from the
source ion. A signal that does not match a definition has `adductName: null`.
Hypotheses require at least two matched signals for groups of up to four inputs,
or three matched signals for larger groups.

## POST `/api/rule-puntuation`

Run the complete expert-system operation: first label the supplied signals, then
fire the decision-table rules for `ruleTarget` and `ionizationMode` against each
surviving hypothesis. The historical `puntuation` spelling is part of the API.

The caller should issue one request for every `(pyOpenMS feature group, CMM lipid
class candidate)` pair it wants to evaluate.

Request:

```json
{
  "features": [
    {"mzValue": 201.007276, "intensity": 1000.0, "retentionTime": 120.0},
    {"mzValue": 222.989218, "intensity": 500.0, "retentionTime": 120.0}
  ],
  "mobilePhases": ["CH3COO"],
  "toleranceMode": "PPM",
  "tolerance": 10.0,
  "ruleTarget": "PC",
  "ionizationMode": "POSITIVE",
  "sampleType": "PLASMA"
}
```

Valid mobile-phase values are `NH4`, `CH3CN`, `CH3OH`, `CH3COO`, and `HCOO`.
Valid targets are `CAR`, `CE`, `CER`, `CHOL`, `DG`, `FA`, `HEXCER`, `LPC`,
`LPC_OP`, `LPE`, `LPE_OP`, `LPI`, `PC`, `PC_OP`, `PE`, `PE_OP`, `PI`, `SM`, and
`TG`. The only current sample type is `PLASMA`.

Response:

```json
{
  "results": [
    {
      "annotatedFeature": {
        "hypothesisId": "hyp-9ef91ba9-48f5-3db0-bf8c-3749f5d64ddc",
        "neutralMass": 200.0,
        "items": [
          {
            "mzValue": 201.007276,
            "intensity": 1000.0,
            "retentionTime": 120.0,
            "adductName": "[M+H]+"
          }
        ]
      },
      "score": 3,
      "normalizedScore": 1.0,
      "hasEvidence": true,
      "evidenceStatus": "SUPPORTING",
      "descrCorrect": "Contains [M+H]+, ...",
      "descrIncorrect": "",
      "appliedPresence": 1,
      "appliedIntensity": 1
    }
  ]
}
```

`score` is the raw weighted evidence sum: occurrence rules contribute ±1 and
relative-intensity rules contribute ±2. The manuscript-defined normalized score
is `score / (appliedPresence + 2 * appliedIntensity)` and ranges from −1 to +1.
A zero normalized score means activated supporting and contradicting evidence
balanced. When no rule generated evidence, `score` remains zero while
`normalizedScore` is JSON `null`, `hasEvidence` is `false`, and
`evidenceStatus` is `"NO_EVIDENCE"`. This is distinct from activated evidence
with `score: 0`, `normalizedScore: 0.0`, `hasEvidence: true`, and
`evidenceStatus: "BALANCED"`. The other statuses are `"SUPPORTING"` and
`"CONTRADICTING"`. Consumers should retain the raw score, rule counts and
descriptions instead of interpreting the normalized score as a calibrated
probability.

No-evidence fields therefore have the following shape:

```json
{
  "score": 0,
  "normalizedScore": null,
  "hasEvidence": false,
  "evidenceStatus": "NO_EVIDENCE",
  "appliedPresence": 0,
  "appliedIntensity": 0
}
```

## POST `/api/reload-rules`

Recompile all six decision-table workbooks and atomically replace the active
Drools container if compilation succeeds.

- Success: HTTP 200, `Rules reloaded successfully`.
- Compilation failure: HTTP 500 containing the Drools build error; the previous
  container remains active.

In an executable-JAR deployment, the workbooks are packaged classpath resources.
Changing source XLSX files therefore requires rebuilding and restarting the JAR;
calling this endpoint alone does not copy external files into the artifact.

## Validation errors

Spring validation returns HTTP 400 when required fields are absent or when enum
values are unknown. `features` and `mobilePhases` (where applicable) must be
non-empty. `ionizationMode` is mandatory so opposite-polarity labels can never
enter an annotation hypothesis.

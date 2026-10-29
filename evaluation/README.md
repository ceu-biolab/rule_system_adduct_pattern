# pyOpenMS evaluation pipeline

The `lipid-eval` command performs the first evaluation stage:

1. load one positive and one negative Martínez iterative mzML acquisition;
2. retain MS1 spectra and centroid profile data when necessary;
3. run pyOpenMS mass-trace and elution-peak detection;
4. assemble isotope-aware ion features with `FeatureFindingMetabo`;
5. group co-eluting features using RT overlap and Pearson trace correlation;
6. write a nested JSON result and a flat CSV result.

That preprocessing command performs no CMM search, adduct labelling, or Drools
rule execution. Positive and negative inputs are never grouped together.

The second, separately invoked stage reads those frozen preprocessing results,
labels correlated groups through the Java API, and searches `compounds.mass`
using each returned neutral-mass hypothesis. It still does not execute rules.

## Pilot inputs

The checked-in configuration uses:

- `NIST_Iterative_20ev_1_POS.mzML`
- `NIST_Iterative_20ev_1_NEG.mzML`

This is a preliminary pilot because the full-scan acquisitions referenced by
the MZmine batch are not currently available.

## Environment and execution

```bash
cd evaluation
conda env create -f requirements.yml
conda activate lipid-rule-evaluation
python -m pip install --no-deps -e .
lipid-eval --config configs/martinez_pilot.json
```

To update an existing environment, run
`conda env update -f requirements.yml --prune`. The environment also includes
Java 21 and Maven for running the API locally; Java libraries remain managed by
the root `pom.xml`.

Results are written to `evaluation/results/martinez_pilot/feature_groups.json`
and `feature_groups.csv`. Singleton groups are retained and explicitly marked;
only groups with more than one feature are eligible for the later annotation
stage. The JSON also records per-polarity QC and every threshold-passing
correlation edge so group membership can be audited.

## Annotation and compound-mass lookup

Start the Java service from the repository root:

```bash
mvn spring-boot:run
```

Then run the second stage in another shell:

```bash
conda activate lipid-rule-evaluation
lipid-annotate --config evaluation/configs/martinez_pilot.json
```

This submits only groups with two or more features. It queries PostgreSQL over
the local socket using peer authentication (`dbname=compounds user=alberto`) and
does not store a password. The mass-only lookup returns every positive
`public.compounds.mass` within 10 ppm; it applies no compound type, status,
charge, or formula filters.

Each compound match is enriched first from the one-to-one
`compounds_lipids_classification` row. If that row is absent or has no parseable
type, a conservative parser extracts only the leading family from
`compound_name` (for example, `PC` from `PC(16:0/18:1)`). The output records the
raw CMM value, the name-derived value, the selected type, its provenance, any
conflict, and the corresponding Java `RuleTarget` when rules exist for that
family. Ether/plasmalogen PC, LPC, PE, and LPE values map to their `_OP` rule
targets.

Outputs are `compound_annotations.json` and `compound_annotations.csv` under
`evaluation/results/martinez_pilot/annotation`. The JSON preserves every peak
label, neutral-mass hypothesis, and compound match. The flat CSV has one row per
hypothesis/compound match and retains explicit rows for unmatched hypotheses,
groups without hypotheses, and skipped singletons.

## Rule execution and final scored annotations

With the Java service running, execute:

```bash
conda activate lipid-rule-evaluation
lipid-rules --config evaluation/configs/martinez_pilot.json
```

The command deduplicates the enriched compound candidates into one API request
per `(group_id, rule_target)`. It calls `POST /api/rule-puntuation` with mobile
phases `NH4` and `CH3OH`, then joins the response back to the stored neutral-mass
hypotheses using `hypothesisId`. Scores are class-level evidence and are not
interpreted as calibrated probabilities.

The primary result is
`evaluation/results/martinez_pilot/rules/final_annotations_with_scores.json`.
It retains the complete group, hypothesis, peak-label and compound hierarchy;
each hypothesis contains its applicable `rule_scores`. A flat candidate-level
CSV and a non-duplicated hypothesis/class score CSV are written alongside it.
Rule-workbook checksums and request reconciliation details are recorded for
reproducibility. Ground-truth metrics remain disabled.

Each rule result preserves the raw weighted score and the manuscript-defined
normalized evidence balance: `score / (applied_presence + 2 ×
applied_intensity)`. The API and Python artifacts represent no evidence as JSON
`null` together with `has_rule_evidence: false` and
`evidence_status: "NO_EVIDENCE"`, so it cannot be confused with a balanced
normalized score of zero (`evidence_status: "BALANCED"`).

## Reference matching and metrics workbook

Generate the Martínez evaluation workbook from the final scored JSON and the
reference table with:

```bash
lipid-metrics \
  --results evaluation/results/martinez/rules/final_annotations_with_scores.json \
  --gold-standard evaluation/results/martinez/gold_standard.xlsx \
  --output evaluation/results/martinez/martinez_evaluation_metrics_20ppm.xlsx \
  --mz-tolerance-ppm 20
```

The evaluator uses the requested updated threshold of **20 ppm** for gold-standard
m/z matching and 6 seconds RT error, and requires polarity, subclass and sum-composition
agreement. Chain-resolved names are collapsed to their sum composition, for
example `PC(16:0/18:1)` becomes `PC(34:1)`. CMM structural records sharing a
group, neutral-mass hypothesis and sum composition are treated as one candidate.
All candidates tied at the maximum normalized score are top-1. MRR uses average
rank for ties, and no-evidence candidates are retained but excluded from ranking
metrics, as specified in the manuscript.

The mass error is `abs(observed_mz - reference_mz) / reference_mz * 1e6`.
For example, the 20 ppm window is 0.002 Da at m/z 100 and 0.020 Da at m/z 1000.
The default is 20 ppm, configurable through `--mz-tolerance-ppm`; the old
`--mz-tolerance-da` argument has been replaced. The `Parameters` worksheet records
the tolerance and its units, and candidate/reference-match sheets include the
mass error in both ppm and Da. This threshold belongs to reference matching;
preprocessing, API ion matching and CMM searches use their own configuration.
The workbook with the `_20ppm` suffix is a separate result from the earlier
`martinez_evaluation_metrics.xlsx` report generated at 0.002 Da.

## Fedorova reference evaluation

The local gold standard is `evaluation/results/fedorova/gold-standard.xlsx`,
sheet `S1`. Its layout differs from Martínez Table S2. The evaluator detects
both layouts automatically, or accepts `--gold-format fedorova` explicitly.
Fedorova requires `--reference-method` to select the RT column matching the
acquisition: `HILIC`, `C18 RPC`, `C30 RPC`, or `C30* RPC`. Run the evaluation
separately for each method using that method's scored results.

The reader uses the normalized LipidLynxX species names (including sum composition
and ether/plasmalogen class targets), derives acquisition polarity from the marked
adduct columns, and converts RT from minutes to seconds. Unlike Table S2, the
Fedorova workbook supplies ion labels rather than numerical ion m/z values.
Reference ion m/z is computed from the reported `Exact_mass` plus the corresponding
Java-catalog ion offset; `[M-H2O+H]+` maps to `[M+H-H2O]+`. Reported masses are used
as supplied. The reader preserves source row and method in each reference record,
and excludes rows lacking an RT for the selected method or a supported rule target.

Once Fedorova scored results exist, for example for C18 RPC, run:

```bash
lipid-metrics \
  --results evaluation/results/fedorova/rules/final_annotations_with_scores.json \
  --gold-standard evaluation/results/fedorova/gold-standard.xlsx \
  --gold-format fedorova \
  --reference-method 'C18 RPC' \
  --mz-tolerance-ppm 20 \
  --output evaluation/results/fedorova/fedorova_C18_evaluation_metrics_20ppm.xlsx
```

At the time of this update, the repository contains the Fedorova gold standard,
but no Fedorova mzML inputs or final scored results. The command above is the
evaluation procedure once those results have been generated; it is not evidence
of a completed Fedorova run.

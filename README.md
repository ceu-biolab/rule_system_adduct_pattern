# Rule System Adduct Pattern – Backend

Spring Boot backend that annotates LC-MS features with adducts and scores them against lipid-class-specific Drools rules.

---

## Running the Project Locally

**Requirements:** Java 21, Maven

```bash
mvn spring-boot:run
```

---

## Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/annotate-feature` | Annotate raw LC-MS features with adduct hypotheses. |
| `POST /api/rule-puntuation` | Annotate features and score them against Drools rules for a specific lipid class. |

---

## Architecture

### Workflow

```
POST /api/rule-puntuation
        │
        ▼
RulePuntuationService.calculatePuntuation()
        │
        ├─► FeatureAnnotationService.transform()
        │         │
        │         ├─► detectCharge()          — isotope spacing analysis per signal
        │         ├─► buildHypothesisGroup()  — neutral mass → brute-force adduct matching
        │         ├─► filter()                — discard low-coverage hypotheses
        │         └─► deduplicate()           — remove identical feature/adduct sets
        │
        │   Returns: List<AnnotatedFeature>
        │
        ├─► buildRulePrefix()               — (RuleTarget, IonizationMode) → "PC_PositiveCheck"
        │
        └─► applyRules()  [per AnnotatedFeature]
                  │
                  ├─► KieSession globals: lipid, mobilePhases, sampleType
                  ├─► KieSession facts:   ResultItem (one per annotated signal)
                  └─► fireAllRules(rule.name.startsWith(prefix))

        Returns: RulePuntuationResponseDTO { List<ScoredFeature> }
```

### Data Structures

**Request (both endpoints)**

```
FeatureInput
  mzValue        Double   — measured mass-to-charge
  intensity      Double   — signal intensity
  retentionTime  Double   — retention time (minutes)

RulePuntuationRequestDTO
  features       List<FeatureInput>
  mobilePhases   List<MobilePhases>   — e.g. [CH3COO, HCOO]
  toleranceMode  ToleranceMode        — PPM | DALTON
  ruleTarget     RuleTarget           — e.g. PC, TG, Cer, SM …
  ionizationMode IonizationMode       — POSITIVE | NEGATIVE
```

**Internal annotation result (shared between both services)**

```
AnnotatedFeature                         — one lipid hypothesis
  items          Set<ResultItem>         — all input signals with assigned adducts
  score          int                     — accumulated rule score (hidden from JSON; exposed via ScoredFeature)
  descrCorrect   String                  — concatenated passing rule descriptions
  descrIncorrect String                  — concatenated failing rule descriptions
  appliedPresence  int                   — number of presence/absence rules fired
  appliedIntensity int                   — number of intensity-order rules fired

ResultItem                               — one signal within a hypothesis
  mzValue        Double
  intensity      Double
  retentionTime  Double
  adductName     String                  — e.g. "[M+H]+", null if unmatched
```

**Rule punctuation response**

```
RulePuntuationResponseDTO
  bestResult     ScoredFeature         — highest-scoring annotation hypothesis

ScoredFeature
  annotatedFeature  AnnotatedFeature   — signal set with adduct assignments
  score             int                — final accumulated score
  descrCorrect      String             — reasons for positive score
  descrIncorrect    String             — reasons for negative score
  appliedPresence   int
  appliedIntensity  int
```

---

## Feature Annotation

### Charge Detection

Before matching, each signal is tested for isotopic spacing against all other signals in the same RT window (`|ΔRT| ≤ 0.02 min`). The first charge state whose expected isotope peak is found within `0.01 Da` is used:

| Charge (z) | Expected M+1 spacing (m/z) |
|---|---|
| 1 | 1.0033 |
| 2 | 0.5016 |
| 3 | 0.3344 |

If no isotope peak is detected the signal is treated as singly charged (z = 1). Only adducts with matching absolute charge are tested for a given signal.

### Neutral Mass Calculation

Once a source signal and its charge are known, a neutral mass hypothesis is computed for each adduct definition:

```
neutralMass = (mz × |z| − offset) / multimer
```

That mass is then used to predict the expected m/z for every other adduct and checked against the remaining signals.

### Adduct Matching

For each `(sourceSignal, sourceAdduct)` pair a **hypothesis group** is built by iterating all input signals and finding the best-matching adduct within tolerance:

- **PPM mode:** `tolerance = mz × 10 / 1_000_000`
- **Dalton mode:** `tolerance = 1.0 Da`

The source signal is always assigned the source adduct. Other signals get the closest matching adduct, or `null` if nothing matches.

### Filtering

After all hypotheses are generated, low-coverage ones are discarded:

| Number of input peaks (N) | Minimum matched adducts required |
|---|---|
| N ≤ 4 | 2 |
| N > 4 | 3 |

Remaining hypotheses are then **deduplicated**: two hypotheses with identical `(mz, intensity, rt, adductName)` sets (order-independent) are collapsed into one.

---

## Rule Punctuation

### Rule Source — Excel Decision Table

All rules live in a single Excel workbook:

```
src/main/resources/rules/AdductRules.drl.xlsx
```

This is a **Drools Decision Table** (DTABLE format). At startup `DroolsConfig` loads the workbook directly — there is no DRL scanning. The individual `.drl` files under `rules/positive/` and `rules/negative/` are kept as reference and can be re-generated from the Excel via the conversion script at `src/main/resources/convertDRLtoExcel/gen_decision_table.py`.

### Rule Selection Within the Session

Rules inside the workbook follow the naming convention:

```
{LipidClass}_{Polarity}Check - <rule description>
```

Examples: `PC_PositiveCheck - Presence [M+H]+`, `Cer_NegativeCheck - Intensity [M+CH3COO]- > [M-H]-`

The service builds a prefix from the request and passes an `AgendaFilter` so only rules for the requested lipid class and polarity fire:

```java
String polarity = mode.name().charAt(0) + mode.name().substring(1).toLowerCase(); // "Positive" / "Negative"
String prefix   = target.getDrlPrefix() + "_" + polarity + "Check";               // e.g. "PC_PositiveCheck"
session.fireAllRules(match -> match.getRule().getName().startsWith(prefix));
```

### Drools Session Setup

Each `AnnotatedFeature` gets its own `KieSession`:

| Mechanism | Value |
|---|---|
| Global `lipid` | The `AnnotatedFeature` being scored |
| Global `mobilePhases` | `List<MobilePhases>` from the request |
| Global `sampleType` | Hardcoded `"PLASMA"` |
| Facts | One `ResultItem` inserted per annotated signal |

### Rule Types

Rules are grouped into four categories. Scores **accumulate** — each rule adds or subtracts from the running total.

**Type 1 — Adduct presence (+1)**
Fires when a specific adduct is found in the working memory. Optionally guarded by `sampleType` or mobile phase.
```
rule "PC_PositiveCheck - Presence [M+H]+"
when
    ResultItem(adductName == "[M+H]+")
    eval(sampleType.equalsIgnoreCase("PLASMA"))
then
    lipid.setScore(1);
```

**Type 2 — Adduct absence (−1)**
Fires when an expected adduct is *absent* from working memory (Drools `not` pattern). Typically guarded by the mobile phase that would produce it.
```
rule "Cer_NegativeCheck - No presence [M+CH3COO]-"
when
    not ResultItem(adductName == "[M+CH3COO]-")
    MobilePhases() from mobilePhases where phase == MobilePhases.CH3COO
then
    lipid.setScore(-1);
```

**Type 3 — Correct intensity order (+2)**
Fires when adduct A has higher intensity than adduct B, matching the expected fragmentation pattern.
```
rule "PC_PositiveCheck - Intensity [M+H]+ > [M+Na]+"
when
    $a1: ResultItem(adductName == "[M+H]+")
    $a2: ResultItem(adductName == "[M+Na]+")
    eval($a1.getIntensity() > $a2.getIntensity())
then
    lipid.setScore(2);
```

**Type 4 — Incorrect intensity order (−2)**
Fires when the observed intensity order is reversed from what is expected.

### Mobile Phase Guards

Some adducts only form in specific solvent systems. Rules use the `mobilePhases` global to condition their firing:

| Adduct | Required phase |
|---|---|
| `[M+CH3COO]-` | `MobilePhases.CH3COO` |
| `[M+HCOO]-` | `MobilePhases.HCOO` |
| `[M+C2H7N2]+` | `NH4` + `CH3CN` + `CH3OH` |

A rule for such an adduct pattern will only fire if the matching phase is present in the `mobilePhases` list.

### Score Interpretation

```
score > 0   — consistent with the expected adduct pattern for this lipid class
score = 0   — no matching rules fired (no evidence either way)
score < 0   — pattern contradicts expectations for this lipid class
```

`appliedPresence` and `appliedIntensity` count how many rules of each type actually fired, allowing downstream consumers to normalise the score.

After scoring all annotation hypotheses, the service returns only the **single highest-scoring** one as `bestResult`. If all candidates score equally (e.g. all zero), the first one encountered is returned.

---

## Supported Lipid Classes

**Positive mode (18):** CAR, CE, Cer, Chol, DG, HexCer, LPC\_OP, LPC, LPE\_OP, LPE, LPI, PC\_OP, PC, PE\_OP, PE, PI, SM, TG

**Negative mode (15):** Cer, DG, FA, HexCer, LPC, LPC\_OP, LPE, LPE\_OP, LPI, PC, PC\_OP, PE, PE\_OP, PI, SM

---

## Project Structure

```
ceu.biolab.cmm
│
├── featureAnnotation/
│   ├── controller/       FeatureAnnotationController
│   ├── service/          FeatureAnnotationService
│   └── dto/              FeatureAnnotationRequestDTO, FeatureAnnotationResultDTO
│
├── rulePuntuation/
│   ├── controller/       RulePuntuationController
│   ├── service/          RulePuntuationService
│   └── dto/              RulePuntuationRequestDTO, RulePuntuationResponseDTO
│
├── shared/
│   ├── domain/           IonizationMode, MobilePhases, RuleTarget, ToleranceMode
│   └── dto/              FeatureAnnotation (AnnotatedFeature, ResultItem)
│
└── config/               DroolsConfig (loads all *.drl at startup)

src/main/resources/
├── adducts/              CSV adduct catalogs (positive / negative)
├── convertDRLtoExcel/    gen_decision_table.py — converts .drl files → AdductRules.drl.xlsx
└── rules/
    ├── AdductRules.drl.xlsx   ← single Drools Decision Table loaded at startup
    ├── positive/         {LipidClass}_PositiveCheck.drl  (18 reference files)
    │   └── userFiles/    user-editable drafts
    └── negative/         {LipidClass}_NegativeCheck.drl  (15 reference files)
        └── userFiles/    user-editable drafts
```

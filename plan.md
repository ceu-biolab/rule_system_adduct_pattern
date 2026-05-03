## Plan: Drools Datamodel Alignment

Create a new datamodel package `io.github.mzmine.datamodel` with POJOs for `FoundLipid`, `FoundAdduct`, and the `MobilePhases` enum that match the rule usage, then update CURRENTPROMPTINFO rules to import these classes and remove unused CommonAdduct imports. This keeps Drools compilation clean for the provided rules without changing their logic.

**Steps**
1. Confirm the contract from [CURRENTPROMPTINFO/metadata_rules.md](CURRENTPROMPTINFO/metadata_rules.md) and the DRL usage, using numeric counters for `appliedPresence`/`appliedIntensity` as chosen.
2. Add POJOs in package `io.github.mzmine.datamodel`:
   - `FoundAdduct`: fields `adductName` (String) and `intensity` (double), no-arg constructor, JavaBean getters/setters (`getAdductName`, `setAdductName`, `getIntensity`, `setIntensity`).
   - `FoundLipid`: fields `score` (double), `descrCorrect` (String), `descrIncorrect` (String), `appliedPresence` (int), `appliedIntensity` (int), `listAdducts` (List<FoundAdduct>), no-arg constructor that initializes `listAdducts` with `ArrayList`, JavaBean getters/setters including `getListAdducts`/`setListAdducts`.
   - `MobilePhases` enum with constants `NH4`, `CH3CN`, `CH3OH`, `CH3COO`, `HCOO`.
3. Update all DRL files under [CURRENTPROMPTINFO/positive](CURRENTPROMPTINFO/positive), [CURRENTPROMPTINFO/negative](CURRENTPROMPTINFO/negative), and their userFiles subfolders to import `io.github.mzmine.datamodel.FoundLipid`, `io.github.mzmine.datamodel.FoundAdduct`, and `io.github.mzmine.datamodel.MobilePhases`.
4. Remove unused `CommonAdductPositive`/`CommonAdductNegative` imports from the CURRENTPROMPTINFO rules to avoid missing-symbol errors.
5. Audit DRL files that reference `FoundLipid` but lack an explicit import (several negative-mode rules) and add the import so Drools can resolve the type.

**Relevant files**
- [CURRENTPROMPTINFO/metadata_rules.md](CURRENTPROMPTINFO/metadata_rules.md) — authoritative contract for methods/attributes.
- [CURRENTPROMPTINFO/positive](CURRENTPROMPTINFO/positive) — positive-mode DRL imports to update.
- [CURRENTPROMPTINFO/negative](CURRENTPROMPTINFO/negative) — negative-mode DRL imports to update.
- [CURRENTPROMPTINFO/positive/userFiles](CURRENTPROMPTINFO/positive/userFiles) — remove unused CommonAdductPositive import.
- [CURRENTPROMPTINFO/negative/userFiles](CURRENTPROMPTINFO/negative/userFiles) — remove unused CommonAdductNegative import.

**Verification**
1. Compile the project (e.g., `mvn -q -DskipTests compile`) to ensure the new datamodel classes build.
2. If these rules are later moved onto the classpath (e.g., into `src/main/resources/rules`), run a Drools build or tests (`mvn -q test`) to confirm rule compilation and symbol resolution.

**Decisions**
- Use `int` counters for `appliedPresence`/`appliedIntensity` to match the rule increments.
- Remove unused CommonAdduct imports from CURRENTPROMPTINFO rules.
- Limit DRL updates to the CURRENTPROMPTINFO rules only.

**Further Considerations**
1. The workspace does not contain the `mzmine-community/src/test/java` path; if you want to reuse those tests, provide that repo or path so they can be indexed.
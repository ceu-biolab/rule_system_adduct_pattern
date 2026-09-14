package ceu.biolab.cmm.shared.domain.adduct;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.shared.domain.IonizationMode;

class AdductCatalogRuleCoverageTest {

    private static final List<String> POSITIVE_RULE_FILES = List.of(
            "/rules/positive_presence.xlsx",
            "/rules/positive_intensityGT.xlsx",
            "/rules/positive_intensityLT.xlsx");
    private static final List<String> NEGATIVE_RULE_FILES = List.of(
            "/rules/negative_presence.xlsx",
            "/rules/negative_intensityGT.xlsx",
            "/rules/negative_intensityLT.xlsx");

    @Test
    void everyPositiveRuleFactLabelExistsInPositiveCatalog() throws IOException {
        assertRuleLabelsCovered(POSITIVE_RULE_FILES, IonizationMode.POSITIVE);
    }

    @Test
    void everyNegativeRuleFactLabelExistsInNegativeCatalog() throws IOException {
        assertRuleLabelsCovered(NEGATIVE_RULE_FILES, IonizationMode.NEGATIVE);
    }

    private void assertRuleLabelsCovered(List<String> ruleFiles, IonizationMode mode) throws IOException {
        Map<String, AdductDefinition> definitions = AdductCatalog.definitionsFor(mode);
        DataFormatter formatter = new DataFormatter();

        for (String path : ruleFiles) {
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertTrue(stream != null, "Missing rule workbook: " + path);
                try (Workbook workbook = WorkbookFactory.create(stream)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    for (Row row : sheet) {
                        assertRuleCellCovered(path, row.getCell(2), formatter, definitions);
                        assertRuleCellCovered(path, row.getCell(3), formatter, definitions);
                    }
                }
            }
        }
    }

    private void assertRuleCellCovered(String path,
                                       Cell cell,
                                       DataFormatter formatter,
                                       Map<String, AdductDefinition> definitions) {
        if (cell == null) return;
        String value = formatter.formatCellValue(cell).trim();
        if (!value.startsWith("[") || !(value.endsWith("+") || value.endsWith("-"))) return;

        assertTrue(definitions.containsKey(value),
                () -> "Rule fact label " + value + " from " + path + " is missing from the catalog");
    }
}

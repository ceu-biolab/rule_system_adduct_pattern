package ceu.biolab.cmm.featureAnnotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationEntryDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.shared.domain.ToleranceMode;

class FeatureAnnotationIntegrationTest {
    private static final double PROTON_MASS = 1.007276;
    private static final double SODIUM_MASS = 22.989218;
    private static final double AMMONIUM_MASS = 18.033823;
    private static final double POTASSIUM_MASS = 38.963158;
    private static final double TWO_H_MASS = 2.014552;
    private static final double ISOTOPE_SPACING = 1.003355;

    @Test
    void simpleDataset_twoPeaksSameRt_shouldKeepHypothesis() {
        // Feature B is at 200.0 + 1.007276, so it matches the [M+H]+ adduct if M = 200.0.
        FeatureAnnotationEntryDTO request = new FeatureAnnotationEntryDTO();
        request.setToleranceMode(ToleranceMode.PPM);
        request.getFeatures().add(buildFeature(200.0, 1000.0, 1.2));
        request.getFeatures().add(buildFeature(200.0 + PROTON_MASS, 500.0, 1.2));

        FeatureAnnotationResultDTO response = new FeatureAnnotationService().transform(request);
        assertNotNull(response);

        boolean found = response.getResults().stream().anyMatch(group -> {
            Optional<FeatureAnnotationResultDTO.ResultItem> a = findItem(group, 200.0);
            Optional<FeatureAnnotationResultDTO.ResultItem> b = findItem(group, 200.0 + PROTON_MASS);
            return a.isPresent() && b.isPresent()
                    && a.get().getAdduct() != null
                    && b.get().getAdduct() != null;
        });

        assertTrue(found);
    }

    @Test
    void complexDataset_detectsNeutralMassAndMatches() {
        // M = 300.0; [M+H]+ = 301.007276, [M+Na]+ = 322.989218, [M+NH4]+ = 318.033823.
        FeatureAnnotationEntryDTO request = new FeatureAnnotationEntryDTO();
        request.setToleranceMode(ToleranceMode.PPM);
        request.getFeatures().add(buildFeature(300.0 + PROTON_MASS, 1200.0, 1.2));
        request.getFeatures().add(buildFeature(300.0 + SODIUM_MASS, 1100.0, 1.2));
        request.getFeatures().add(buildFeature(300.0 + AMMONIUM_MASS, 900.0, 1.2));
        // Isotope peak for [M+H]+: 301.007276 + 1.003355.
        request.getFeatures().add(buildFeature(300.0 + PROTON_MASS + ISOTOPE_SPACING, 700.0, 1.2));
        // [M+2H]2+ = (M + 2.014552) / 2.
        request.getFeatures().add(buildFeature((300.0 + TWO_H_MASS) / 2.0, 600.0, 1.2));
        request.getFeatures().add(buildFeature(500.0, 500.0, 1.2));

        FeatureAnnotationResultDTO response = new FeatureAnnotationService().transform(request);
        assertNotNull(response);

        boolean found = response.getResults().stream().anyMatch(group -> {
            Optional<FeatureAnnotationResultDTO.ResultItem> h = findItem(group, 300.0 + PROTON_MASS);
            Optional<FeatureAnnotationResultDTO.ResultItem> na = findItem(group, 300.0 + SODIUM_MASS);
            Optional<FeatureAnnotationResultDTO.ResultItem> nh4 = findItem(group, 300.0 + AMMONIUM_MASS);
            Optional<FeatureAnnotationResultDTO.ResultItem> z2 = findItem(group, (300.0 + TWO_H_MASS) / 2.0);
            return h.isPresent() && na.isPresent() && nh4.isPresent() && z2.isPresent()
                    && "[M+H]+".equals(h.get().getAdduct())
                    && "[M+Na]+".equals(na.get().getAdduct())
                    && "[M+NH4]+".equals(nh4.get().getAdduct())
                    && "[M+2H]2+".equals(z2.get().getAdduct());
        });

        assertTrue(found);
    }

    @Test
    void rtMismatch_discardsAllResults() {
        // RT mismatch (1.2 vs 5.0) should prevent adduct support across peaks.
        FeatureAnnotationEntryDTO request = new FeatureAnnotationEntryDTO();
        request.setToleranceMode(ToleranceMode.PPM);
        request.getFeatures().add(buildFeature(200.0, 1000.0, 1.2));
        request.getFeatures().add(buildFeature(200.0 + SODIUM_MASS, 800.0, 5.0));

        FeatureAnnotationResultDTO response = new FeatureAnnotationService().transform(request);
        assertNotNull(response);
        assertEquals(0, response.getResults().size());
    }

    @Test
    void filterThreshold_discardsWhenNGreaterThanFour() {
        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotationResultDTO.AnnotatedFeature hypothesis = new FeatureAnnotationResultDTO.AnnotatedFeature();

        // Two matches and three nulls simulate a noisy dataset with only two supported peaks.
        hypothesis.getItems().add(buildResultItem(200.0, "[M+H]+"));
        hypothesis.getItems().add(buildResultItem(200.0 + SODIUM_MASS, "[M+Na]+"));
        hypothesis.getItems().add(buildResultItem(300.0, null));
        hypothesis.getItems().add(buildResultItem(350.0, null));
        hypothesis.getItems().add(buildResultItem(400.0, null));

        List<FeatureAnnotationResultDTO.AnnotatedFeature> filtered =
                service.filter(List.of(hypothesis), 5);

        assertEquals(0, filtered.size());
    }

    private FeatureAnnotationEntryDTO.FeatureInput buildFeature(double mz, double intensity, double rt) {
        FeatureAnnotationEntryDTO.FeatureInput input = new FeatureAnnotationEntryDTO.FeatureInput();
        input.setMzValue(mz);
        input.setIntensity(intensity);
        input.setRetentionTime(rt);
        return input;
    }

    private FeatureAnnotationResultDTO.ResultItem buildResultItem(double mz, String adduct) {
        FeatureAnnotationResultDTO.ResultItem item = new FeatureAnnotationResultDTO.ResultItem();
        item.setMzValue(mz);
        item.setIntensity(100.0);
        item.setRetentionTime(1.0);
        item.setAdduct(adduct);
        return item;
    }

    private Optional<FeatureAnnotationResultDTO.ResultItem> findItem(
            FeatureAnnotationResultDTO.AnnotatedFeature group,
            double mz) {
        return group.getItems().stream()
                .filter(item -> Math.abs(item.getMzValue() - mz) <= 0.0001)
                .findFirst();
    }

    private List<MockAdduct> setupMockAducts() {
        List<MockAdduct> adducts = new ArrayList<>();
        adducts.add(new MockAdduct("[M+H]+", PROTON_MASS));
        adducts.add(new MockAdduct("[M+Na]+", SODIUM_MASS));
        return adducts;
    }

    private static class MockAdduct {
        private final String canonical;
        private final double offset;

        private MockAdduct(String canonical, double offset) {
            this.canonical = canonical;
            this.offset = offset;
        }
    }
}

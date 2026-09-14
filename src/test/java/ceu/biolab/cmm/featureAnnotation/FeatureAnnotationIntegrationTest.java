package ceu.biolab.cmm.featureAnnotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

class FeatureAnnotationIntegrationTest {
    private static final double PROTON_MASS    = 1.007276;
    private static final double SODIUM_MASS    = 22.989218;
    private static final double AMMONIUM_MASS  = 18.033823;
    private static final double TWO_H_MASS     = 2.014552;
    private static final double ISOTOPE_SPACING = 1.003355;
    private static final double FORMATE_MASS = 44.998201;
    private static final double ACETATE_MASS = 59.013851;
    private static final double SODIUM_ACETATE_MASS = 82.003070;
    private static final double DIAMMONIUM_ACETONITRILE_MASS = 59.060374;
    private static final double CHOLESTADIENE_MZ = 369.351578;

    private final FeatureAnnotationService service = new FeatureAnnotationService();

    // ── existing pipeline tests ───────────────────────────────────────────────

    @Test
    void simpleDataset_twoPeaksSameRt_shouldKeepHypothesis() {
        FeatureAnnotationResultDTO response = service.transform(request(ToleranceMode.PPM, null,
                feature(200.0 + PROTON_MASS, 1000.0),
                feature(200.0 + SODIUM_MASS,  500.0)));

        assertTrue(findHypothesis(response, 200.0 + PROTON_MASS, "[M+H]+",
                                             200.0 + SODIUM_MASS,  "[M+Na]+"));
    }

    @Test
    void complexDataset_detectsNeutralMassAndMatches() {
        FeatureAnnotationResultDTO response = service.transform(request(ToleranceMode.PPM, null,
                feature(300.0 + PROTON_MASS,                   1200.0),
                feature(300.0 + SODIUM_MASS,                   1100.0),
                feature(300.0 + AMMONIUM_MASS,                  900.0),
                feature(300.0 + PROTON_MASS + ISOTOPE_SPACING,  700.0),
                feature((300.0 + TWO_H_MASS) / 2.0,            600.0),
                feature(500.0,                                   500.0)));

        assertTrue(response.getResults().stream().anyMatch(g ->
                item(g, 300.0 + PROTON_MASS).filter(i -> "[M+H]+".equals(i.getAdductName())).isPresent()
             && item(g, 300.0 + SODIUM_MASS).filter(i -> "[M+Na]+".equals(i.getAdductName())).isPresent()
             && item(g, 300.0 + AMMONIUM_MASS).filter(i -> "[M+NH4]+".equals(i.getAdductName())).isPresent()
             && item(g, (300.0 + TWO_H_MASS) / 2.0).filter(i -> "[M+2H]2+".equals(i.getAdductName())).isPresent()));
    }

    @Test
    void filterThreshold_discardsWhenNGreaterThanFour() {
        FeatureAnnotation.AnnotatedFeature h = hypothesis(
                resultItem(200.0, "[M+H]+"),
                resultItem(200.0 + SODIUM_MASS, "[M+Na]+"),
                resultItem(300.0, null),
                resultItem(350.0, null),
                resultItem(400.0, null));

        assertEquals(0, service.filter(List.of(h), 5).size());
    }

    // ── new: edge cases ───────────────────────────────────────────────────────

    @Test
    void transform_nullInput_returnsEmpty() {
        FeatureAnnotationResultDTO result = service.transform(null);
        assertNotNull(result);
        assertTrue(result.getResults().isEmpty());
    }

    @Test
    void transform_emptyFeatures_returnsEmpty() {
        FeatureAnnotationRequestDTO req = new FeatureAnnotationRequestDTO();
        req.setIonizationMode(IonizationMode.POSITIVE);
        req.setToleranceMode(ToleranceMode.PPM);
        assertTrue(service.transform(req).getResults().isEmpty());
    }

    @Test
    void transform_singleSignal_returnsEmpty() {
        assertTrue(service.transform(request(ToleranceMode.PPM, null,
                feature(200.0, 1000.0))).getResults().isEmpty());
    }

    // ── new: tolerance mode ───────────────────────────────────────────────────

    @Test
    void transform_daltonMode_findsTwoAdducts() {
        FeatureAnnotationResultDTO response = service.transform(request(ToleranceMode.DALTON, null,
                feature(200.0 + PROTON_MASS, 1000.0),
                feature(200.0 + SODIUM_MASS,  500.0)));

        assertTrue(findHypothesis(response, 200.0 + PROTON_MASS, "[M+H]+",
                                             200.0 + SODIUM_MASS,  "[M+Na]+"));
    }

    // ── new: custom tolerance ─────────────────────────────────────────────────

    /**
     * Na+ signal placed 5 mDa off its expected position.
     * At mz≈222: default 10 ppm ≈ 2.2 mDa → miss; custom 30 ppm ≈ 6.7 mDa → hit.
     */
    @Test
    void transform_customPpmTolerance_widensWindow_findsMatch() {
        double mzH  = 200.0 + PROTON_MASS;
        double mzNa = 200.0 + SODIUM_MASS + 0.005; // 5 mDa off

        FeatureAnnotationResultDTO result = service.transform(request(ToleranceMode.PPM, 30.0,
                feature(mzH, 1000.0), feature(mzNa, 500.0)));

        assertTrue(result.getResults().stream().anyMatch(g ->
                item(g, mzH).filter(i -> "[M+H]+".equals(i.getAdductName())).isPresent()
             && item(g, mzNa).filter(i -> "[M+Na]+".equals(i.getAdductName())).isPresent()));
    }

    @Test
    void transform_defaultPpm_missesSameOffset() {
        double mzH  = 200.0 + PROTON_MASS;
        double mzNa = 200.0 + SODIUM_MASS + 0.005; // same 5 mDa off, no custom tolerance

        assertTrue(service.transform(request(ToleranceMode.PPM, null,
                feature(mzH, 1000.0), feature(mzNa, 500.0))).getResults().isEmpty());
    }

    // ── new: filter threshold N≤4 ─────────────────────────────────────────────

    @Test
    void filter_smallDataset_twoMatches_passes() {
        FeatureAnnotation.AnnotatedFeature h = hypothesis(
                resultItem(200.0, "[M+H]+"),
                resultItem(221.98, "[M+Na]+"));

        assertEquals(1, service.filter(List.of(h), 2).size());
    }

    @Test
    void filter_smallDataset_oneMatch_rejected() {
        FeatureAnnotation.AnnotatedFeature h = hypothesis(
                resultItem(200.0, "[M+H]+"),
                resultItem(221.98, null));

        assertTrue(service.filter(List.of(h), 2).isEmpty());
    }

    // ── new: deduplication ────────────────────────────────────────────────────

    @Test
    void filter_deduplicatesIdenticalHypotheses() {
        FeatureAnnotation.AnnotatedFeature h1 = hypothesis(
                resultItem(200.0, "[M+H]+"), resultItem(221.98, "[M+Na]+"));
        FeatureAnnotation.AnnotatedFeature h2 = hypothesis(
                resultItem(200.0, "[M+H]+"), resultItem(221.98, "[M+Na]+"));

        assertEquals(1, service.filter(List.of(h1, h2), 2).size());
    }

    // ── polarity and rule-vocabulary coverage ───────────────────────────────

    @Test
    void transform_positiveMode_neverAssignsNegativeAdducts() {
        FeatureAnnotationResultDTO response = service.transform(request(IonizationMode.POSITIVE,
                ToleranceMode.PPM, null,
                feature(200.0 + PROTON_MASS, 1000.0),
                feature(200.0 + SODIUM_MASS, 500.0)));

        assertTrue(response.getResults().stream()
                .flatMap(group -> group.getItems().stream())
                .filter(item -> item.getAdductName() != null)
                .allMatch(item -> item.getAdductName().endsWith("+")));
    }

    @Test
    void transform_negativeMode_neverAssignsPositiveAdducts() {
        FeatureAnnotationResultDTO response = service.transform(request(IonizationMode.NEGATIVE,
                ToleranceMode.PPM, null,
                feature(200.0 - PROTON_MASS, 1000.0),
                feature(200.0 + FORMATE_MASS, 500.0)));

        assertTrue(findHypothesis(response, 200.0 - PROTON_MASS, "[M-H]-",
                200.0 + FORMATE_MASS, "[M+HCOO]-"));
        assertTrue(response.getResults().stream()
                .flatMap(group -> group.getItems().stream())
                .filter(item -> item.getAdductName() != null)
                .allMatch(item -> item.getAdductName().endsWith("-")));
    }

    @Test
    void transform_labelsRuleSpecificPositiveAdductAndDiagnosticIon() {
        double neutralMass = 600.0;
        FeatureAnnotationResultDTO adductResponse = service.transform(request(IonizationMode.POSITIVE,
                ToleranceMode.PPM, null,
                feature(neutralMass + PROTON_MASS, 1000.0),
                feature(neutralMass + DIAMMONIUM_ACETONITRILE_MASS, 500.0)));
        FeatureAnnotationResultDTO diagnosticResponse = service.transform(request(IonizationMode.POSITIVE,
                ToleranceMode.PPM, null,
                feature(neutralMass + AMMONIUM_MASS, 1000.0),
                feature(CHOLESTADIENE_MZ, 500.0)));

        assertTrue(findHypothesis(adductResponse, neutralMass + PROTON_MASS, "[M+H]+",
                neutralMass + DIAMMONIUM_ACETONITRILE_MASS, "[M+C2H7N2]+"));
        assertTrue(findHypothesis(diagnosticResponse, neutralMass + AMMONIUM_MASS, "[M+NH4]+",
                CHOLESTADIENE_MZ, "[C27H44]+"));
    }

    @Test
    void transform_labelsNegativeSodiumAcetateCluster() {
        double neutralMass = 700.0;
        FeatureAnnotationResultDTO response = service.transform(request(IonizationMode.NEGATIVE,
                ToleranceMode.PPM, null,
                feature(neutralMass + ACETATE_MASS, 1000.0),
                feature(neutralMass + ACETATE_MASS + SODIUM_ACETATE_MASS, 500.0)));

        assertTrue(findHypothesis(response, neutralMass + ACETATE_MASS, "[M+CH3COO]-",
                neutralMass + ACETATE_MASS + SODIUM_ACETATE_MASS,
                "[M+CH3COO+(CH3COONa)]-"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureAnnotationRequestDTO request(ToleranceMode mode, Double tolerance,
                                                FeatureAnnotationRequestDTO.FeatureInput... features) {
        return request(IonizationMode.POSITIVE, mode, tolerance, features);
    }

    private FeatureAnnotationRequestDTO request(IonizationMode ionizationMode,
                                                ToleranceMode mode,
                                                Double tolerance,
                                                FeatureAnnotationRequestDTO.FeatureInput... features) {
        FeatureAnnotationRequestDTO req = new FeatureAnnotationRequestDTO();
        req.setIonizationMode(ionizationMode);
        req.setToleranceMode(mode);
        req.setTolerance(tolerance);
        for (var f : features) req.getFeatures().add(f);
        return req;
    }

    private FeatureAnnotationRequestDTO.FeatureInput feature(double mz, double intensity) {
        FeatureAnnotationRequestDTO.FeatureInput f = new FeatureAnnotationRequestDTO.FeatureInput();
        f.setMzValue(mz);
        f.setIntensity(intensity);
        f.setRetentionTime(1.0);
        return f;
    }

    private FeatureAnnotation.AnnotatedFeature hypothesis(FeatureAnnotation.ResultItem... items) {
        FeatureAnnotation.AnnotatedFeature h = new FeatureAnnotation.AnnotatedFeature();
        for (var i : items) h.getItems().add(i);
        return h;
    }

    private FeatureAnnotation.ResultItem resultItem(double mz, String adduct) {
        FeatureAnnotation.ResultItem item = new FeatureAnnotation.ResultItem();
        item.setMzValue(mz);
        item.setIntensity(100.0);
        item.setRetentionTime(1.0);
        item.setAdductName(adduct);
        return item;
    }

    private Optional<FeatureAnnotation.ResultItem> item(FeatureAnnotation.AnnotatedFeature g, double mz) {
        return g.getItems().stream()
                .filter(i -> Math.abs(i.getMzValue() - mz) <= 0.0001)
                .findFirst();
    }

    private boolean findHypothesis(FeatureAnnotationResultDTO response,
                                   double mz1, String adduct1, double mz2, String adduct2) {
        return response.getResults().stream().anyMatch(g ->
                item(g, mz1).filter(i -> adduct1.equals(i.getAdductName())).isPresent()
             && item(g, mz2).filter(i -> adduct2.equals(i.getAdductName())).isPresent());
    }
}

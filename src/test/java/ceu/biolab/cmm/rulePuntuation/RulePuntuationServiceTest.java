package ceu.biolab.cmm.rulePuntuation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.config.KieContainerProvider;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.service.RulePuntuationService;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.domain.RuleTarget;
import ceu.biolab.cmm.shared.domain.SampleType;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation.AnnotatedFeature;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation.ResultItem;

/**
 * Verifies that the Drools scoring rules correctly rank a feature higher when
 * scored against the lipid class that matches its actual adduct pattern than
 * when scored against a different class.
 *
 * No Spring context needed: KieContainer is built from DroolsConfig directly,
 * and scoreFeature bypasses the featureAnnotation step.
 */
class RulePuntuationServiceTest {

    private static RulePuntuationService service;

    @BeforeAll
    static void buildService() {
        KieContainerProvider provider = new KieContainerProvider();
        service = new RulePuntuationService(provider, new FeatureAnnotationService());
    }

    // -------------------------------------------------------------------------
    // Positive-mode tests
    // -------------------------------------------------------------------------

    /**
     * DG positive pattern: Na > H > K, [M+H-H2O]+ present.
     * Expected: DG score > PC score.
     */
    @Test
    void dgPositivePattern_dgScoresHigherThanPc() {
        AnnotatedFeature feature = feature(
                item("[M+Na]+",     1000.0),
                item("[M+H]+",       500.0),
                item("[M+K]+",       200.0),
                item("[M+H-H2O]+",   300.0)
        );
        List<MobilePhases> phases = List.of(MobilePhases.CH3COO);

        int dgScore = service.scoreFeature(feature, phases, RuleTarget.DG, IonizationMode.POSITIVE, SampleType.PLASMA);
        int pcScore = service.scoreFeature(feature, phases, RuleTarget.PC, IonizationMode.POSITIVE, SampleType.PLASMA);

        assertAll(
                () -> assertTrue(dgScore > pcScore,
                        "DG feature: DG score (%d) should beat PC score (%d)".formatted(dgScore, pcScore))
        );
    }

    /**
     * PC positive pattern: H > Na > K.
     * Expected: PC score > DG score.
     */
    @Test
    void pcPositivePattern_pcScoresHigherThanDg() {
        AnnotatedFeature feature = feature(
                item("[M+H]+",  1000.0),
                item("[M+Na]+",  500.0),
                item("[M+K]+",   100.0)
        );
        List<MobilePhases> phases = List.of(MobilePhases.CH3COO);

        int pcScore = service.scoreFeature(feature, phases, RuleTarget.PC, IonizationMode.POSITIVE, SampleType.PLASMA);
        int dgScore = service.scoreFeature(feature, phases, RuleTarget.DG, IonizationMode.POSITIVE, SampleType.PLASMA);

        assertAll(
                () -> assertTrue(pcScore > dgScore,
                        "PC feature: PC score (%d) should beat DG score (%d)".formatted(pcScore, dgScore))
        );
    }

    /**
     * CE positive pattern: Na > K > H, diagnostic [C27H44]+ present.
     * Expected: CE score > PC score.
     */
    @Test
    void cePositivePattern_ceScoresHigherThanPc() {
        AnnotatedFeature feature = feature(
                item("[M+Na]+",   1000.0),
                item("[M+K]+",     700.0),
                item("[C27H44]+",  500.0),
                item("[M+H]+",     200.0)
        );
        List<MobilePhases> phases = List.of(MobilePhases.CH3COO);

        int ceScore = service.scoreFeature(feature, phases, RuleTarget.CE, IonizationMode.POSITIVE, SampleType.PLASMA);
        int pcScore = service.scoreFeature(feature, phases, RuleTarget.PC, IonizationMode.POSITIVE, SampleType.PLASMA);

        assertAll(
                () -> assertTrue(ceScore > pcScore,
                        "CE feature: CE score (%d) should beat PC score (%d)".formatted(ceScore, pcScore))
        );
    }

    /**
     * TG positive pattern with NH4/CH3CN/CH3OH phases:
     * C2H7N2 > NH4 > Na > K > H.
     * Expected: TG score > PC score.
     */
    @Test
    void tgPositiveWithNH4Phases_tgScoresHigherThanPc() {
        AnnotatedFeature feature = feature(
                item("[M+C2H7N2]+", 1200.0),
                item("[M+NH4]+",    1000.0),
                item("[M+Na]+",      500.0),
                item("[M+K]+",       200.0),
                item("[M+H]+",       100.0)
        );
        List<MobilePhases> phases = List.of(
                MobilePhases.NH4, MobilePhases.CH3CN, MobilePhases.CH3OH);

        int tgScore = service.scoreFeature(feature, phases, RuleTarget.TG, IonizationMode.POSITIVE, SampleType.PLASMA);
        int pcScore = service.scoreFeature(feature, phases, RuleTarget.PC, IonizationMode.POSITIVE, SampleType.PLASMA);

        assertAll(
                () -> assertTrue(tgScore > pcScore,
                        "TG feature: TG score (%d) should beat PC score (%d)".formatted(tgScore, pcScore))
        );
    }

    // -------------------------------------------------------------------------
    // Negative-mode test
    // -------------------------------------------------------------------------

    /**
     * PC negative pattern: decreasing CH3COO adduct cluster
     * [M+CH3COO]- > [M+CH3COO+(CH3COONa)]- > ... > [M+Cl]-
     * Expected: PC_NEG score > DG_NEG score.
     */
    @Test
    void pcNegativePattern_pcScoresHigherThanDg() {
        AnnotatedFeature feature = feature(
                item("[M+CH3COO]-",                1000.0),
                item("[M+CH3COO+(CH3COONa)]-",      500.0),
                item("[M+CH3COO+(CH3COONa)2]-",     200.0),
                item("[M+CH3COO+(CH3COONa)3]-",     100.0),
                item("[M+Cl]-",                      50.0)
        );
        List<MobilePhases> phases = List.of(MobilePhases.CH3COO, MobilePhases.HCOO);

        int pcScore = service.scoreFeature(feature, phases, RuleTarget.PC, IonizationMode.NEGATIVE, SampleType.PLASMA);
        int dgScore = service.scoreFeature(feature, phases, RuleTarget.DG, IonizationMode.NEGATIVE, SampleType.PLASMA);

        assertAll(
                () -> assertTrue(pcScore > dgScore,
                        "PC-NEG feature: PC score (%d) should beat DG score (%d)".formatted(pcScore, dgScore))
        );
    }

    /** The REST-facing orchestration must pass polarity into feature annotation. */
    @Test
    void calculatePuntuation_propagatesPositivePolarityToAnnotation() {
        RulePuntuationRequestDTO request = new RulePuntuationRequestDTO();
        request.setIonizationMode(IonizationMode.POSITIVE);
        request.setToleranceMode(ToleranceMode.PPM);
        request.setTolerance(10.0);
        request.setRuleTarget(RuleTarget.PC);
        request.setSampleType(SampleType.PLASMA);
        request.setMobilePhases(List.of(MobilePhases.CH3COO));
        request.setFeatures(List.of(
                input(501.007276, 1000.0),
                input(522.989218, 500.0)));

        var response = service.calculatePuntuation(request);

        assertTrue(!response.getResults().isEmpty());
        assertTrue(response.getResults().stream()
                .flatMap(result -> result.getAnnotatedFeature().getItems().stream())
                .filter(result -> result.getAdductName() != null)
                .allMatch(result -> result.getAdductName().endsWith("+")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ResultItem item(String adduct, double intensity) {
        ResultItem item = new ResultItem();
        item.setMzValue(500.0);
        item.setRetentionTime(1.0);
        item.setIntensity(intensity);
        item.setAdductName(adduct);
        return item;
    }

    private static FeatureAnnotationRequestDTO.FeatureInput input(double mz, double intensity) {
        FeatureAnnotationRequestDTO.FeatureInput input = new FeatureAnnotationRequestDTO.FeatureInput();
        input.setMzValue(mz);
        input.setIntensity(intensity);
        input.setRetentionTime(120.0);
        return input;
    }

    private static AnnotatedFeature feature(ResultItem... items) {
        AnnotatedFeature feature = new AnnotatedFeature();
        Set<ResultItem> set = new LinkedHashSet<>();
        for (ResultItem item : items) {
            set.add(item);
        }
        feature.setItems(set);
        return feature;
    }
}

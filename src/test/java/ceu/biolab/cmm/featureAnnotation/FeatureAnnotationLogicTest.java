package ceu.biolab.cmm.featureAnnotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

class FeatureAnnotationLogicTest {
    private static final double PROTON_MASS = 1.007276;
    private static final double SODIUM_MASS = 22.989218;
    private static final double ISOTOPE_SPACING_Z2 = 0.5016;

    @Test
    void detectCharge_z2AndNeutralMass() throws Exception {
        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotationRequestDTO.FeatureInput peakA = buildFeature(200.0, 1000.0, 1.5);
        FeatureAnnotationRequestDTO.FeatureInput peakB = buildFeature(200.0 + ISOTOPE_SPACING_Z2, 500.0, 1.5);
        List<FeatureAnnotationRequestDTO.FeatureInput> signals = List.of(peakA, peakB);

        int charge = (int) invokeDetectCharge(service, peakA, signals, ToleranceMode.PPM);
        assertEquals(2, charge);

        AdductDefinition twoH = AdductCatalog.definitionsFor(IonizationMode.POSITIVE).get("[M+2H]2+");
        assertNotNull(twoH);

        double expectedNeutral = 200.0 * 2.0 - (2.0 * PROTON_MASS);
        double neutralMass = (double) invokeCalculateNeutralMass(service, 200.0, twoH);
        assertEquals(expectedNeutral, neutralMass, 0.0001);
    }

    @Test
    void adductCombination_success() {
        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotationRequestDTO request = new FeatureAnnotationRequestDTO();
        request.setToleranceMode(ToleranceMode.PPM);

        double neutralMass = 200.0 - PROTON_MASS;
        double sodiumMz = neutralMass + SODIUM_MASS;

        request.getFeatures().add(buildFeature(200.0, 1000.0, 1.5));
        request.getFeatures().add(buildFeature(sodiumMz, 800.0, 1.5));

        FeatureAnnotationResultDTO response = service.transform(request);
        assertNotNull(response);

        boolean found = response.getResults().stream().anyMatch(group -> {
            Optional<FeatureAnnotation.ResultItem> hItem = findItem(group, 200.0);
            Optional<FeatureAnnotation.ResultItem> naItem = findItem(group, sodiumMz);
            return hItem.isPresent()
                    && naItem.isPresent()
                    && "[M+H]+".equals(hItem.get().getAdductName())
                    && "[M+Na]+".equals(naItem.get().getAdductName());
        });

        assertTrue(found);
    }

    @Test
    void filterThresholds_largeDatasetRequiresThreeMatches() {
        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotation.AnnotatedFeature hypothesis = new FeatureAnnotation.AnnotatedFeature();

        hypothesis.getItems().add(buildResultItem(200.0, "[M+H]+"));
        hypothesis.getItems().add(buildResultItem(221.98, "[M+Na]+"));
        hypothesis.getItems().add(buildResultItem(300.0, null));
        hypothesis.getItems().add(buildResultItem(350.0, null));
        hypothesis.getItems().add(buildResultItem(400.0, null));

        List<FeatureAnnotation.AnnotatedFeature> filtered = service.filter(List.of(hypothesis), 5);

        assertEquals(0, filtered.size());
    }

    private FeatureAnnotationRequestDTO.FeatureInput buildFeature(double mz, double intensity, double rt) {
        FeatureAnnotationRequestDTO.FeatureInput input = new FeatureAnnotationRequestDTO.FeatureInput();
        input.setMzValue(mz);
        input.setIntensity(intensity);
        input.setRetentionTime(rt);
        return input;
    }

    private FeatureAnnotation.ResultItem buildResultItem(double mz, String adductName) {
        FeatureAnnotation.ResultItem item = new FeatureAnnotation.ResultItem();
        item.setMzValue(mz);
        item.setIntensity(100.0);
        item.setRetentionTime(1.0);
        item.setAdductName(adductName);
        return item;
    }

    private Optional<FeatureAnnotation.ResultItem> findItem(FeatureAnnotation.AnnotatedFeature group, double mz) {
        return group.getItems().stream()
                .filter(item -> Math.abs(item.getMzValue() - mz) <= 0.0001)
                .findFirst();
    }

    private Object invokeDetectCharge(FeatureAnnotationService service,
                                      FeatureAnnotationRequestDTO.FeatureInput signal,
                                      List<FeatureAnnotationRequestDTO.FeatureInput> signals,
                                      ToleranceMode toleranceMode) throws Exception {
        Method method = FeatureAnnotationService.class.getDeclaredMethod("detectCharge",
                FeatureAnnotationRequestDTO.FeatureInput.class, List.class, ToleranceMode.class, Double.class);
        method.setAccessible(true);
        return method.invoke(service, signal, signals, toleranceMode, null);
    }

    private Object invokeCalculateNeutralMass(FeatureAnnotationService service,
                                              double mz,
                                              AdductDefinition adduct) throws Exception {
        Method method = FeatureAnnotationService.class.getDeclaredMethod("calculateTheoreticalMass",
                double.class, AdductDefinition.class);
        method.setAccessible(true);
        return method.invoke(service, mz, adduct);
    }
}

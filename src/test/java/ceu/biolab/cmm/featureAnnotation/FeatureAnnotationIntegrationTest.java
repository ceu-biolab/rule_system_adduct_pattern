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
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

class FeatureAnnotationIntegrationTest {
    private static final double PROTON_MASS = 1.007276;
    private static final double SODIUM_MASS = 22.989218;
    private static final double AMMONIUM_MASS = 18.033823;
    private static final double TWO_H_MASS = 2.014552;
    private static final double ISOTOPE_SPACING = 1.003355;

    @Test
    void simpleDataset_twoPeaksSameRt_shouldKeepHypothesis() {
        FeatureAnnotationRequestDTO request = new FeatureAnnotationRequestDTO();
        request.setToleranceMode(ToleranceMode.PPM);
        request.getFeatures().add(buildFeature(200.0 + PROTON_MASS, 1000.0, 1.2));
        request.getFeatures().add(buildFeature(200.0 + SODIUM_MASS, 500.0, 1.2));

        FeatureAnnotationResultDTO response = new FeatureAnnotationService().transform(request);
        assertNotNull(response);

        boolean found = response.getResults().stream().anyMatch(group -> {
            Optional<FeatureAnnotation.ResultItem> h = findItem(group, 200.0 + PROTON_MASS);
            Optional<FeatureAnnotation.ResultItem> na = findItem(group, 200.0 + SODIUM_MASS);
            return h.isPresent() && na.isPresent()
                    && "[M+H]+".equals(h.get().getAdductName())
                    && "[M+Na]+".equals(na.get().getAdductName());
        });

        assertTrue(found);
    }

    @Test
    void complexDataset_detectsNeutralMassAndMatches() {
        FeatureAnnotationRequestDTO request = new FeatureAnnotationRequestDTO();
        request.setToleranceMode(ToleranceMode.PPM);
        request.getFeatures().add(buildFeature(300.0 + PROTON_MASS, 1200.0, 1.2));
        request.getFeatures().add(buildFeature(300.0 + SODIUM_MASS, 1100.0, 1.2));
        request.getFeatures().add(buildFeature(300.0 + AMMONIUM_MASS, 900.0, 1.2));
        request.getFeatures().add(buildFeature(300.0 + PROTON_MASS + ISOTOPE_SPACING, 700.0, 1.2));
        request.getFeatures().add(buildFeature((300.0 + TWO_H_MASS) / 2.0, 600.0, 1.2));
        request.getFeatures().add(buildFeature(500.0, 500.0, 1.2));

        FeatureAnnotationResultDTO response = new FeatureAnnotationService().transform(request);
        assertNotNull(response);

        boolean found = response.getResults().stream().anyMatch(group -> {
            Optional<FeatureAnnotation.ResultItem> h = findItem(group, 300.0 + PROTON_MASS);
            Optional<FeatureAnnotation.ResultItem> na = findItem(group, 300.0 + SODIUM_MASS);
            Optional<FeatureAnnotation.ResultItem> nh4 = findItem(group, 300.0 + AMMONIUM_MASS);
            Optional<FeatureAnnotation.ResultItem> z2 = findItem(group, (300.0 + TWO_H_MASS) / 2.0);
            return h.isPresent() && na.isPresent() && nh4.isPresent() && z2.isPresent()
                    && "[M+H]+".equals(h.get().getAdductName())
                    && "[M+Na]+".equals(na.get().getAdductName())
                    && "[M+NH4]+".equals(nh4.get().getAdductName())
                    && "[M+2H]2+".equals(z2.get().getAdductName());
        });

        assertTrue(found);
    }

    @Test
    void rtMismatch_discardsAllResults() {
        FeatureAnnotationRequestDTO request = new FeatureAnnotationRequestDTO();
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
        FeatureAnnotation.AnnotatedFeature hypothesis = new FeatureAnnotation.AnnotatedFeature();

        hypothesis.getItems().add(buildResultItem(200.0, "[M+H]+"));
        hypothesis.getItems().add(buildResultItem(200.0 + SODIUM_MASS, "[M+Na]+"));
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
}

package ceu.biolab.cmm.featureAnnotation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.featureAnnotation.dto.AnnotatedFeatureDTO;
import ceu.biolab.cmm.featureAnnotation.dto.AnnotatedFeatureGroupDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequest;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResponse;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;

class FeatureAnnotationServiceTest {

    @Test
    void annotateFeatures_groupsByTheoreticalMass() {
        FeatureAnnotationRequest request = new FeatureAnnotationRequest();
        request.setTolerance(0.05);
        request.setFeatures(List.of(
                buildFeature(200.0, 1000.0, 1.2),
                buildFeature(201.0073, 500.0, 2.8)));

        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotationResponse response = service.annotateFeatures(request);

        assertNotNull(response);
        assertNotNull(response.getAnnotatedFeatures());
        assertFalse(response.getAnnotatedFeatures().isEmpty());

        for (AnnotatedFeatureGroupDTO group : response.getAnnotatedFeatures()) {
            assertNotNull(group.getAnnotatedFeatures());
            assertEquals(2, group.getAnnotatedFeatures().size(), "Each group should include all features");

            List<Double> candidateMasses = new ArrayList<>();
            for (AnnotatedFeatureDTO annotated : group.getAnnotatedFeatures()) {
                assertNotNull(annotated.getAdduct(), "Adduct should be set after filtering");
                AdductDefinition def = resolveDefinition(annotated.getAdduct()).orElseThrow();
                double candidateMass = (annotated.getMzValue() * def.absoluteCharge() - def.offset()) / def.multimer();
                candidateMasses.add(candidateMass);
            }

            double min = candidateMasses.stream().min(Double::compare).orElse(0.0);
            double max = candidateMasses.stream().max(Double::compare).orElse(0.0);
            assertTrue(max - min <= request.getTolerance(), "Group masses should fall within tolerance window");
        }
    }

    @Test
    void annotateFeatures_filtersGroupsBelowThreshold() {
        FeatureAnnotationRequest request = new FeatureAnnotationRequest();
        request.setTolerance(0.001);
        request.setFeatures(List.of(
                buildFeature(200.0, 1000.0, 1.2),
                buildFeature(300.0, 500.0, 2.8)));

        FeatureAnnotationService service = new FeatureAnnotationService();
        FeatureAnnotationResponse response = service.annotateFeatures(request);

        assertNotNull(response);
        assertNotNull(response.getAnnotatedFeatures());
        assertTrue(response.getAnnotatedFeatures().isEmpty(), "No groups should pass the match threshold");
    }

    private FeatureDTO buildFeature(double mz, double intensity, double rt) {
        FeatureDTO feature = new FeatureDTO();
        feature.setMzValue(mz);
        feature.setIntensity(intensity);
        feature.setRetentionTime(rt);
        return feature;
    }

    private Optional<AdductDefinition> resolveDefinition(String canonical) {
        for (IonizationMode mode : IonizationMode.values()) {
            AdductDefinition def = AdductCatalog.definitionsFor(mode).get(canonical);
            if (def != null) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }
}

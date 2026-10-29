package ceu.biolab.cmm.rulePuntuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO.EvidenceStatus;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation.AnnotatedFeature;

/** Verifies the evidence normalization defined in the manuscript. */
class EvidenceScoreNormalizationTest {

    @Test
    void normalizesUsingActivatedRuleWeights() {
        AnnotatedFeature feature = new AnnotatedFeature();
        feature.setScore(1);
        feature.setAppliedPresence(1);
        feature.setAppliedIntensity(1);

        assertEquals(1.0 / 3.0, feature.getNormalizedScore(), 1e-12);
        assertTrue(feature.hasEvidence());
    }

    @Test
    void preservesBalancedEvidenceAsZero() {
        AnnotatedFeature feature = new AnnotatedFeature();
        feature.setAppliedPresence(2);

        assertEquals(0.0, feature.getNormalizedScore());
    }

    @Test
    void returnsNullWhenNoRuleGeneratedEvidence() {
        AnnotatedFeature feature = new AnnotatedFeature();

        assertNull(feature.getNormalizedScore());
        assertFalse(feature.hasEvidence());
    }

    @Test
    void reachesNegativeOneForEntirelyContradictingEvidence() {
        AnnotatedFeature feature = new AnnotatedFeature();
        feature.setScore(-3);
        feature.setAppliedPresence(1);
        feature.setAppliedIntensity(1);

        assertEquals(-1.0, feature.getNormalizedScore());
    }

    @Test
    void classifiesEvidenceWithoutConflatingEmptyAndBalancedScores() {
        assertEquals(EvidenceStatus.NO_EVIDENCE, EvidenceStatus.from(0, false));
        assertEquals(EvidenceStatus.BALANCED, EvidenceStatus.from(0, true));
        assertEquals(EvidenceStatus.SUPPORTING, EvidenceStatus.from(1, true));
        assertEquals(EvidenceStatus.CONTRADICTING, EvidenceStatus.from(-1, true));
    }
}

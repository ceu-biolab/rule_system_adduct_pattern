package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.List;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from expert-system scoring for one grouped input feature. */
@Data
public class RulePuntuationResponseDTO {

    /** Scored annotation hypotheses in deterministic annotation order. */
    private List<ScoredFeature> results;

    /** One adduct-labelling hypothesis and the Drools evidence accumulated for it. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredFeature {

        private FeatureAnnotation.AnnotatedFeature annotatedFeature;
        private int score;
        private String descrCorrect;
        private String descrIncorrect;
        private int appliedPresence;
        private int appliedIntensity;
    }
}

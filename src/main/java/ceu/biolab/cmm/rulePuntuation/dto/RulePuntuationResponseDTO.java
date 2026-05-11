package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.List;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class RulePuntuationResponseDTO {

    private List<ScoredFeature> results;

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

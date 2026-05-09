package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of the rule-punctuation flow: one scored entry per candidate annotation. */
@Data
public class RulePuntuationResponseDTO {

    private List<ScoredFeature> results = new ArrayList<>();

    /** One candidate annotation together with its Drools-computed score. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredFeature {
        /** The annotated feature (adduct assignments, mz/intensity/RT per peak). */
        private FeatureAnnotation.AnnotatedFeature annotatedFeature;
        /** Cumulative score after all matching rules have fired. */
        private int score;
        /** Descriptions of rules that scored positively. */
        private String descrCorrect;
        /** Descriptions of rules that scored negatively. */
        private String descrIncorrect;
    }
}

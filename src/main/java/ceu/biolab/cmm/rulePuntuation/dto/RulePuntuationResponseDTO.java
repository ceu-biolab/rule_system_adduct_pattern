package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.List;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from expert-system scoring for one grouped input feature. */
@Data
public class RulePuntuationResponseDTO {

    /** Qualitative interpretation of the evidence contributed by activated rules. */
    public enum EvidenceStatus {
        /** No occurrence or intensity rule generated evidence. */
        NO_EVIDENCE,
        /** The weighted sum of activated evidence is positive. */
        SUPPORTING,
        /** Supporting and contradicting activated evidence cancel exactly. */
        BALANCED,
        /** The weighted sum of activated evidence is negative. */
        CONTRADICTING;

        /**
         * Classify a raw score while keeping an empty evidence set distinct from
         * a balanced score.
         *
         * @param score raw weighted evidence score
         * @param hasEvidence whether at least one rule contributed evidence
         * @return corresponding evidence status
         */
        public static EvidenceStatus from(int score, boolean hasEvidence) {
            if (!hasEvidence) {
                return NO_EVIDENCE;
            }
            if (score > 0) {
                return SUPPORTING;
            }
            if (score < 0) {
                return CONTRADICTING;
            }
            return BALANCED;
        }
    }

    /** Scored annotation hypotheses in deterministic annotation order. */
    private List<ScoredFeature> results;

    /** One adduct-labelling hypothesis and the Drools evidence accumulated for it. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredFeature {

        private FeatureAnnotation.AnnotatedFeature annotatedFeature;
        /**
         * Accumulated weighted evidence: occurrence rules contribute +/-1 and
         * relative-intensity rules contribute +/-2.
         */
        private int score;

        /**
         * Evidence balance calculated as
         * {@code score / (appliedPresence + 2 * appliedIntensity)}. Its range is
         * [-1, 1], or {@code null} when no rule generated evidence.
         */
        private Double normalizedScore;

        /** Whether at least one occurrence or intensity rule generated evidence. */
        private boolean hasEvidence;

        /** Explicitly distinguishes no evidence from a balanced score of zero. */
        private EvidenceStatus evidenceStatus;

        private String descrCorrect;
        private String descrIncorrect;
        private int appliedPresence;
        private int appliedIntensity;
    }
}

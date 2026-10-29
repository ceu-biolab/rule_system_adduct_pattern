package ceu.biolab.cmm.shared.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Shared response models for neutral-mass/adduct annotation hypotheses. */
public class FeatureAnnotation {

    /**
     * One inferred neutral-mass hypothesis and the measured signals labelled
     * relative to that hypothesis.
     */
    @Data
    public static class AnnotatedFeature {
        /** Stable identifier derived from the neutral mass and labelled signals. */
        private String hypothesisId;

        /** Neutral monoisotopic mass inferred from the source ion hypothesis. */
        private Double neutralMass;

        @NotEmpty
        @Valid
        private Set<ResultItem> items = new LinkedHashSet<>();

        private int score;
        private String descrCorrect = "";
        private String descrIncorrect = "";
        private int appliedPresence;
        private int appliedIntensity;

        @JsonIgnore
        public int getScore() { return score; }

        /**
         * Add one rule's evidence contribution to the accumulated score.
         *
         * @param delta signed evidence contribution
         */
        public void setScore(int delta) { this.score += delta; }

        @JsonIgnore
        public String getDescrCorrect() { return descrCorrect; }

        public void setDescrCorrect(String part) { this.descrCorrect += part; }

        @JsonIgnore
        public String getDescrIncorrect() { return descrIncorrect; }

        public void setDescrIncorrect(String part) { this.descrIncorrect += part; }

        @JsonIgnore
        public int getAppliedPresence() { return appliedPresence; }

        public void setAppliedPresence(int appliedPresence) { this.appliedPresence = appliedPresence; }

        @JsonIgnore
        public int getAppliedIntensity() { return appliedIntensity; }

        public void setAppliedIntensity(int appliedIntensity) { this.appliedIntensity = appliedIntensity; }

        /**
         * Calculate the manuscript's evidence-balance score.
         *
         * <p>Activated occurrence rules have absolute weight one and activated
         * intensity rules have absolute weight two. When no rule generated
         * evidence, the denominator is zero and the result is {@code null}.</p>
         *
         * @return raw score divided by the maximum absolute activated evidence,
         *         or {@code null} when there is no evidence
         */
        @JsonIgnore
        public Double getNormalizedScore() {
            int maximumAbsoluteScore = appliedPresence + (2 * appliedIntensity);
            return maximumAbsoluteScore == 0
                    ? null
                    : (double) score / maximumAbsoluteScore;
        }

        /**
         * Determine whether at least one rule generated non-zero evidence.
         *
         * @return {@code true} when an occurrence or intensity rule contributed
         */
        @JsonIgnore
        public boolean hasEvidence() {
            return appliedPresence > 0 || appliedIntensity > 0;
        }

        public Set<ResultItem> getItems() { return items; }

        public void setItems(Set<ResultItem> items) {
            this.items = items == null ? new LinkedHashSet<>() : new LinkedHashSet<>(items);
        }

        /** Clears all scoring state so the feature can be re-scored against a different target. */
        public void reset() {
            this.score = 0;
            this.descrCorrect = "";
            this.descrIncorrect = "";
            this.appliedPresence = 0;
            this.appliedIntensity = 0;
        }
    }

    /** One measured signal and its optional polarity-specific ion label. */
    @Data
    public static class ResultItem {
        @NotNull
        private Double mzValue;

        @NotNull
        private Double intensity;

        @NotNull
        private Double retentionTime;

        private String adductName;
    }
}

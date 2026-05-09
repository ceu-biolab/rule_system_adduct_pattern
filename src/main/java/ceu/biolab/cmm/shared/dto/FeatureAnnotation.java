package ceu.biolab.cmm.shared.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class FeatureAnnotation {

    @Data
    public static class AnnotatedFeature {
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

        /** Each rule call adds its delta — never replaces. */
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

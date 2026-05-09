package ceu.biolab.cmm.shared.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
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

        private List<ResultItem> listAdducts = new ArrayList<>();
        private int score;
        private String descrCorrect = "";
        private String descrIncorrect = "";
        private int appliedPresence;
        private int appliedIntensity;

        public List<ResultItem> getListAdducts() {
            return listAdducts;
        }

        public void setListAdducts(List<ResultItem> listAdducts) {
            this.listAdducts = listAdducts == null ? new ArrayList<>() : new ArrayList<>(listAdducts);
            this.items = new LinkedHashSet<>(this.listAdducts);
        }

        public int getScore() {
            return score;
        }

        /** Adds {@code delta} to the running score (accumulates like setDescrCorrect). */
        public void setScore(int delta) {
            this.score += delta;
        }

        /** Resets all scoring state so the same feature can be re-scored with a different target. */
        public void reset() {
            this.score = 0;
            this.descrCorrect = "";
            this.descrIncorrect = "";
            this.appliedPresence = 0;
            this.appliedIntensity = 0;
        }

        public String getDescrCorrect() {
            return descrCorrect;
        }

        public void setDescrCorrect(String descrCorrect) {
            this.descrCorrect = this.descrCorrect + descrCorrect;
        }

        public String getDescrIncorrect() {
            return descrIncorrect;
        }

        public void setDescrIncorrect(String descrIncorrect) {
            this.descrIncorrect = this.descrIncorrect + descrIncorrect;
        }

        public int getAppliedPresence() {
            return appliedPresence;
        }

        public void setAppliedPresence(int appliedPresence) {
            this.appliedPresence = appliedPresence;
        }

        public int getAppliedIntensity() {
            return appliedIntensity;
        }

        public void setAppliedIntensity(int appliedIntensity) {
            this.appliedIntensity = appliedIntensity;
        }

        public Set<ResultItem> getItems() {
            return items;
        }

        public void setItems(Set<ResultItem> items) {
            this.items = items == null ? new LinkedHashSet<>() : new LinkedHashSet<>(items);
            this.listAdducts = new ArrayList<>(this.items);
        }
    }

    @Data
    public static class ResultItem {
        @NotNull
        @JsonProperty("mzValue")
        private Double mzValue;

        @NotNull
        @JsonProperty("intensity")
        private Double intensity;

        @NotNull
        @JsonProperty("retentionTime")
        private Double retentionTime;

        @JsonProperty("adduct")
        private String adduct;

        public String getAdductName() {
            return adduct;
        }

        public void setAdductName(String adductName) {
            this.adduct = adductName;
        }
    }
}

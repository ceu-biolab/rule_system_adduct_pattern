package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeatureAnnotationResultDTO {
    @NotEmpty
    @Valid
    private Set<AnnotatedFeature> results = new LinkedHashSet<>();

    @Data
    public static class AnnotatedFeature {
        @NotEmpty
        @Valid
        private Set<ResultItem> items = new LinkedHashSet<>();
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
    }
}

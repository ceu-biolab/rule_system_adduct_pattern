package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.shared.domain.ToleranceMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeatureAnnotationRequestDTO {
    @NotEmpty
    @Valid
    private List<FeatureInput> features = new ArrayList<>();

    @NotNull
    private ToleranceMode toleranceMode;

    @Data
    public static class FeatureInput {
        @NotNull
        @JsonProperty("mzValue")
        private Double mzValue;

        @NotNull
        @JsonProperty("intensity")
        private Double intensity;

        @NotNull
        @JsonProperty("retentionTime")
        private Double retentionTime;
    }
}

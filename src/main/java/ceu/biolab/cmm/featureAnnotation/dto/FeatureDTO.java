package ceu.biolab.cmm.featureAnnotation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class FeatureDTO {
    @NotNull
    @Positive
    private Double mzValue;

    @PositiveOrZero
    private Double intensity;

    @NotNull
    @PositiveOrZero
    private Double retentionTime;
}

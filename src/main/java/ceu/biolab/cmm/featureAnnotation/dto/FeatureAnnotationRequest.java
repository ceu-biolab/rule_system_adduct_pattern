package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class FeatureAnnotationRequest {

    private static final double DEFAULT_TOLERANCE = 0.05;

    @NotEmpty
    private List<@Valid FeatureDTO> features;

    @PositiveOrZero
    private Double tolerance;

    public FeatureAnnotationRequest() {
        this.tolerance = DEFAULT_TOLERANCE;
    }
}

package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

/**
 * Response containing the surviving adduct-labelling hypotheses for one input
 * feature group.
 */
@Data
public class FeatureAnnotationResultDTO {
    /** Distinct hypotheses after matched-ion filtering and deduplication. */
    @NotEmpty
    @Valid
    private Set<FeatureAnnotation.AnnotatedFeature> results = new LinkedHashSet<>();
}

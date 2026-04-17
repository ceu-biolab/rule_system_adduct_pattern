package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Data
public class FeatureAnnotationResultDTO {
    @NotEmpty
    @Valid
    private Set<FeatureAnnotation.AnnotatedFeature> results = new LinkedHashSet<>();
}

package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;

@Data
public class AnnotatedFeatureGroupDTO {
    private Set<AnnotatedFeatureDTO> annotatedFeatures = new LinkedHashSet<>();
}

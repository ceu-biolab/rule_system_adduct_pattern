package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AnnotatedFeatureGroupDTO {
    private List<AnnotatedFeatureDTO> annotatedFeatures = new ArrayList<>();
}

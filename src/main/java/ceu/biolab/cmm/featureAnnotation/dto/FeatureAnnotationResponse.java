package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.List;
import lombok.Data;

@Data
public class FeatureAnnotationResponse {

    private List<AnnotatedFeatureGroupDTO> annotatedFeatures;

}

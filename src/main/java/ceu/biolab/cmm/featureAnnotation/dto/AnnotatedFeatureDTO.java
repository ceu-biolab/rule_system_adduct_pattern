package ceu.biolab.cmm.featureAnnotation.dto;

import lombok.Data;

@Data
public class AnnotatedFeatureDTO {
    private Double mzValue;
    private Double intensity;
    private Double retentionTime;
    private String adduct;
}

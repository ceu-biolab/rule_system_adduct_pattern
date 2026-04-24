package ceu.biolab.cmm.rulePuntuation.dto;

import lombok.Data;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Data
public class RulePuntuationRequestDTO {
    //private String targetCandidate;
    private FeatureAnnotation.AnnotatedFeature feature;
    private double score;
}

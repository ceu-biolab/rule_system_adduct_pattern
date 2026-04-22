package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Data
public class RulePuntuationRequestDTO {
    //private String targetCandidate;
    private List<FeatureAnnotation.ResultItem> features = new ArrayList<>();
    private double score;
}

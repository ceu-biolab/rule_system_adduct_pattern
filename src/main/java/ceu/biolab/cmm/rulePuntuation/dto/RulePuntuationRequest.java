package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.shared.domain.msFeature.AnnotatedFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class RulePuntuationRequest {
    @NotEmpty
    private List<@Valid AnnotatedFeature> features = new ArrayList<>();
}

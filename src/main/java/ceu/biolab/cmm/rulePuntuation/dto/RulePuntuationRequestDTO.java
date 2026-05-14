package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.domain.RuleTarget;
import ceu.biolab.cmm.shared.domain.SampleType;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RulePuntuationRequestDTO {

    @NotEmpty
    @Valid
    private List<FeatureAnnotationRequestDTO.FeatureInput> features = new ArrayList<>();

    @NotEmpty
    private List<MobilePhases> mobilePhases = new ArrayList<>();

    @NotNull
    private ToleranceMode toleranceMode;

    @NotNull
    private RuleTarget ruleTarget;

    @NotNull
    private IonizationMode ionizationMode;

    private SampleType sampleType = SampleType.PLASMA;
}

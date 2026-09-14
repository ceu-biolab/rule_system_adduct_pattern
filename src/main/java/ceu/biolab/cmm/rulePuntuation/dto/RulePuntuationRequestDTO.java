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

/**
 * Request for the complete adduct-labelling and expert-system scoring flow.
 *
 * <p>The feature list must contain one group produced by external
 * deconvolution/feature grouping. The target normally comes from a mass-based
 * compound candidate returned by CMM.</p>
 */
@Data
public class RulePuntuationRequestDTO {

    /** Signals belonging to exactly one deconvoluted feature group. */
    @NotEmpty
    @Valid
    private List<FeatureAnnotationRequestDTO.FeatureInput> features = new ArrayList<>();

    /** Mobile-phase components exposed to guarded Drools rules. */
    @NotEmpty
    private List<MobilePhases> mobilePhases = new ArrayList<>();

    /** Unit used to interpret {@link #tolerance}. */
    @NotNull
    private ToleranceMode toleranceMode;

    /** Lipid class whose rule subset should be fired. */
    @NotNull
    private RuleTarget ruleTarget;

    /** Acquisition polarity used both for adduct labelling and rule selection. */
    @NotNull
    private IonizationMode ionizationMode;

    /** Biological sample type; currently only plasma is supported. */
    private SampleType sampleType = SampleType.PLASMA;

    /** Optional matching tolerance; defaults are selected by {@code toleranceMode}. */
    private Double tolerance;
}

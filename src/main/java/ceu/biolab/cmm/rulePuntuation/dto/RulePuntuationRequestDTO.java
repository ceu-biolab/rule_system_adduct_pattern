package ceu.biolab.cmm.rulePuntuation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.domain.RuleTarget;
import ceu.biolab.cmm.shared.domain.Tolerance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Input payload for the rule-punctuation flow.
 *
 * <p>The service will:
 * <ol>
 *   <li>Forward {@code features} + {@code tolerance.mode} to the featureAnnotation service
 *       to obtain candidate {@code AnnotatedFeature} objects.</li>
 *   <li>Score each candidate by running only the Drools rules that match
 *       {@code ruleTarget} + {@code ionizationMode}.</li>
 * </ol>
 *
 * <p>Note: {@code tolerance.value} is stored for future use; the featureAnnotation
 * service currently applies its own default tolerance constants per mode.
 */
@Data
public class RulePuntuationRequestDTO {

    /** Raw MS peaks to annotate. Each point carries mz, intensity and retentionTime. */
    @NotEmpty
    @Valid
    private List<FeatureAnnotationRequestDTO.FeatureInput> features = new ArrayList<>();

    /**
     * Mobile phases present in the chromatographic run.
     * Used by Drools rules to enable/disable phase-dependent adduct rules
     * (e.g. [M+C2H7N2]+ only scores when NH4 + CH3CN + CH3OH are present).
     */
    @NotEmpty
    private List<MobilePhases> mobilePhases = new ArrayList<>();

    /** Mass tolerance used during the annotation step. */
    @NotNull
    @Valid
    private Tolerance tolerance;

    /** Lipid class whose Drools rules will be applied (e.g. PC, TG, FA). */
    @NotNull
    private RuleTarget ruleTarget;

    /**
     * Ionization polarity.
     * Together with {@code ruleTarget} this selects the exact DRL rule set:
     * {@code {ruleTarget.drlPrefix}_{Positive|Negative}Check}.
     */
    @NotNull
    private IonizationMode ionizationMode;
}

package ceu.biolab.cmm.rulePuntuation.service;

import java.util.ArrayList;
import java.util.List;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.domain.RuleTarget;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Service
public class RulePuntuationService {

    private static final Logger logger = LoggerFactory.getLogger(RulePuntuationService.class);

    // All current rules guard on this value; kept internal until the DTO exposes it.
    private static final String SAMPLE_TYPE = "PLASMA";

    private final KieContainer kieContainer;
    private final FeatureAnnotationService featureAnnotationService;

    public RulePuntuationService(KieContainer kieContainer,
                                  FeatureAnnotationService featureAnnotationService) {
        this.kieContainer = kieContainer;
        this.featureAnnotationService = featureAnnotationService;
    }

    /**
     * Full punctuation flow:
     * <ol>
     *   <li>Delegate annotation to {@link FeatureAnnotationService}.</li>
     *   <li>Score every candidate annotation with Drools, restricting rules
     *       to the prefix {@code {ruleTarget}_{Positive|Negative}Check}.</li>
     * </ol>
     */
    public RulePuntuationResponseDTO calculatePuntuation(RulePuntuationRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("RulePuntuationRequestDTO must not be null.");
        }

        FeatureAnnotationResultDTO annotationResult =
                featureAnnotationService.transform(buildAnnotationRequest(request));

        String rulePrefix = buildRulePrefix(request.getRuleTarget(), request.getIonizationMode());
        logger.info("Scoring {} candidate annotations with rule prefix '{}'",
                annotationResult.getResults().size(), rulePrefix);

        RulePuntuationResponseDTO response = new RulePuntuationResponseDTO();
        for (FeatureAnnotation.AnnotatedFeature feature : annotationResult.getResults()) {
            applyRules(feature, request.getMobilePhases(), rulePrefix);

            RulePuntuationResponseDTO.ScoredFeature scored = new RulePuntuationResponseDTO.ScoredFeature(
                    feature,
                    feature.getScore(),
                    feature.getDescrCorrect(),
                    feature.getDescrIncorrect());
            response.getResults().add(scored);
        }

        return response;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FeatureAnnotationRequestDTO buildAnnotationRequest(RulePuntuationRequestDTO request) {
        FeatureAnnotationRequestDTO dto = new FeatureAnnotationRequestDTO();
        dto.setFeatures(new ArrayList<>(request.getFeatures()));
        dto.setToleranceMode(request.getTolerance().getMode());
        return dto;
    }

    /**
     * Scores a pre-built {@link FeatureAnnotation.AnnotatedFeature} directly against the rules
     * for the given target and polarity, without going through the annotation step.
     * Resets all scoring state on {@code feature} before firing rules.
     */
    public int scoreFeature(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            RuleTarget ruleTarget,
                            IonizationMode ionizationMode) {
        feature.reset();
        applyRules(feature, mobilePhases, buildRulePrefix(ruleTarget, ionizationMode));
        return feature.getScore();
    }

    /**
     * Runs the Drools session for a single annotated feature.
     * Only rules whose name starts with {@code rulePrefix} are fired.
     *
     * <p>Globals required by every DRL file:
     * <ul>
     *   <li>{@code lipid} – the mutable {@link FeatureAnnotation.AnnotatedFeature} being scored</li>
     *   <li>{@code mobilePhases} – controls phase-dependent adduct rules</li>
     *   <li>{@code sampleType} – currently always "PLASMA"</li>
     * </ul>
     * {@link FeatureAnnotation.ResultItem} objects are inserted as Drools facts so that
     * presence/intensity conditions can match against them.
     */
    private void applyRules(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            String rulePrefix) {
        KieSession session = kieContainer.newKieSession();
        try {
            session.setGlobal("lipid", feature);
            session.setGlobal("mobilePhases", mobilePhases);
            session.setGlobal("sampleType", SAMPLE_TYPE);

            for (FeatureAnnotation.ResultItem item : feature.getItems()) {
                if (item != null) {
                    session.insert(item);
                }
            }

            int fired = session.fireAllRules(
                    match -> match.getRule().getName().startsWith(rulePrefix));
            logger.debug("Fired {} rules for prefix '{}', score={}",
                    fired, rulePrefix, feature.getScore());
        } finally {
            session.dispose();
        }
    }

    /**
     * Builds the rule-name prefix used to filter the Drools agenda.
     * Maps {@code POSITIVE} → "Positive", {@code NEGATIVE} → "Negative"
     * to match the DRL file naming convention {@code {Class}_{Polarity}Check}.
     */
    private String buildRulePrefix(RuleTarget target, IonizationMode mode) {
        String polarity = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
        return target.getDrlPrefix() + "_" + polarity + "Check";
    }
}

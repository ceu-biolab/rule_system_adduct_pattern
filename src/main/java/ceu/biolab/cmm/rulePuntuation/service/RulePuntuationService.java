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

    private static final String SAMPLE_TYPE = "PLASMA";

    private final KieContainer kieContainer;
    private final FeatureAnnotationService featureAnnotationService;

    public RulePuntuationService(KieContainer kieContainer,
                                  FeatureAnnotationService featureAnnotationService) {
        this.kieContainer = kieContainer;
        this.featureAnnotationService = featureAnnotationService;
    }

    /**
     * Full flow: annotate raw features, then score each candidate against the
     * Drools rules for the requested lipid class and polarity.
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
            response.getResults().add(new RulePuntuationResponseDTO.ScoredFeature(
                    feature,
                    feature.getScore(),
                    feature.getDescrCorrect(),
                    feature.getDescrIncorrect(),
                    feature.getAppliedPresence(),
                    feature.getAppliedIntensity()));
        }

        return response;
    }

    /**
     * Scores a pre-built feature directly, bypassing the annotation step.
     * Resets all scoring state before firing rules.
     */
    public int scoreFeature(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            RuleTarget ruleTarget,
                            IonizationMode ionizationMode) {
        feature.reset();
        applyRules(feature, mobilePhases, buildRulePrefix(ruleTarget, ionizationMode));
        return feature.getScore();
    }

    private FeatureAnnotationRequestDTO buildAnnotationRequest(RulePuntuationRequestDTO request) {
        FeatureAnnotationRequestDTO dto = new FeatureAnnotationRequestDTO();
        dto.setFeatures(new ArrayList<>(request.getFeatures()));
        dto.setToleranceMode(request.getToleranceMode());
        return dto;
    }

    private void applyRules(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            String rulePrefix) {
        KieSession session = kieContainer.newKieSession();
        try {
            session.setGlobal("lipid", feature);
            session.setGlobal("mobilePhases", mobilePhases);
            session.setGlobal("sampleType", SAMPLE_TYPE);

            for (FeatureAnnotation.ResultItem item : feature.getItems()) {
                if (item != null) session.insert(item);
            }

            int fired = session.fireAllRules(
                    match -> match.getRule().getName().startsWith(rulePrefix));
            logger.debug("Fired {} rules for prefix '{}', score={}",
                    fired, rulePrefix, feature.getScore());
        } finally {
            session.dispose();
        }
    }

    /** Maps e.g. (PC, POSITIVE) → "PC_PositiveCheck" to match DRL file naming. */
    private String buildRulePrefix(RuleTarget target, IonizationMode mode) {
        String polarity = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
        return target.getDrlPrefix() + "_" + polarity + "Check";
    }
}

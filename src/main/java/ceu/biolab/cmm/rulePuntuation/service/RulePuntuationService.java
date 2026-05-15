package ceu.biolab.cmm.rulePuntuation.service;

import java.util.ArrayList;
import java.util.List;

import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.config.KieContainerProvider;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.domain.RuleTarget;
import ceu.biolab.cmm.shared.domain.SampleType;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Service
public class RulePuntuationService {

    private static final Logger logger = LoggerFactory.getLogger(RulePuntuationService.class);

    private final KieContainerProvider kieContainerProvider;
    private final FeatureAnnotationService featureAnnotationService;

    public RulePuntuationService(KieContainerProvider kieContainerProvider,
                                  FeatureAnnotationService featureAnnotationService) {
        this.kieContainerProvider = kieContainerProvider;
        this.featureAnnotationService = featureAnnotationService;
    }

    /**
     * Annotate raw features and score every candidate against the Drools rules
     * for the requested lipid class and polarity.
     *
     * @param request rule punctuation input payload
     * @return response containing the scored annotation candidates
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

        List<RulePuntuationResponseDTO.ScoredFeature> scored = new ArrayList<>();
        for (FeatureAnnotation.AnnotatedFeature feature : annotationResult.getResults()) {
            applyRules(feature, request.getMobilePhases(), rulePrefix, request.getSampleType());
            scored.add(new RulePuntuationResponseDTO.ScoredFeature(
                    feature,
                    feature.getScore(),
                    feature.getDescrCorrect(),
                    feature.getDescrIncorrect(),
                    feature.getAppliedPresence(),
                    feature.getAppliedIntensity()));
        }

        RulePuntuationResponseDTO response = new RulePuntuationResponseDTO();
        response.setResults(scored);
        return response;
    }

    /**
     * Score a pre-built feature directly, bypassing the annotation step.
     * Resets all scoring state on the feature before firing rules.
     *
     * @param feature        annotated feature to score
     * @param mobilePhases   mobile phases present in the sample
     * @param ruleTarget     lipid class whose rules should be applied
     * @param ionizationMode polarity used during acquisition
     * @param sampleType     type of biological sample
     * @return accumulated score after all matching rules have fired
     */
    public int scoreFeature(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            RuleTarget ruleTarget,
                            IonizationMode ionizationMode,
                            SampleType sampleType) {
        feature.reset();
        applyRules(feature, mobilePhases, buildRulePrefix(ruleTarget, ionizationMode), sampleType);
        return feature.getScore();
    }

    /**
     * Translate a rule punctuation request into the format expected by the
     * feature annotation service.
     *
     * @param request rule punctuation input payload
     * @return feature annotation input payload
     */
    private FeatureAnnotationRequestDTO buildAnnotationRequest(RulePuntuationRequestDTO request) {
        FeatureAnnotationRequestDTO dto = new FeatureAnnotationRequestDTO();
        dto.setFeatures(new ArrayList<>(request.getFeatures()));
        dto.setToleranceMode(request.getToleranceMode());
        dto.setTolerance(request.getTolerance());
        return dto;
    }

    /**
     * Open a Drools session, insert the feature's result items as facts, set the
     * required globals, and fire only the rules whose name starts with the given
     * prefix.
     *
     * @param feature      annotated feature to evaluate
     * @param mobilePhases mobile phases to expose as a global
     * @param rulePrefix   agenda filter prefix (e.g. "PC_PositiveCheck")
     * @param sampleType   type of biological sample to expose as a global
     */
    private void applyRules(FeatureAnnotation.AnnotatedFeature feature,
                            List<MobilePhases> mobilePhases,
                            String rulePrefix,
                            SampleType sampleType) {
        KieSession session = kieContainerProvider.getContainer().newKieSession();
        try {
            session.setGlobal("lipid", feature);
            session.setGlobal("mobilePhases", mobilePhases);
            session.setGlobal("sampleType", sampleType.name());

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

    /**
     * Derive the rule name prefix from the lipid class and polarity so that only
     * the relevant rules fire (e.g. {@code RuleTarget.PC} + {@code POSITIVE} →
     * {@code "PC_PositiveCheck"}).
     *
     * @param target lipid class
     * @param mode   ionization polarity
     * @return rule name prefix used as the agenda filter
     */
    private String buildRulePrefix(RuleTarget target, IonizationMode mode) {
        String polarity = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
        return target.getDrlPrefix() + "_" + polarity + "Check";
    }
}

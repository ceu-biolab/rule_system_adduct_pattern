package ceu.biolab.cmm.rulePuntuation.service;

import java.util.List;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequest;
import ceu.biolab.cmm.shared.domain.msFeature.Annotation;
import ceu.biolab.cmm.shared.domain.msFeature.AnnotatedFeature;
import ceu.biolab.cmm.shared.domain.msFeature.AnnotationsByAdduct;
import ceu.biolab.cmm.shared.domain.msFeature.Score;

@Service
public class RulePuntuationService {

    private static final Logger logger = LoggerFactory.getLogger(RulePuntuationService.class);

    private final KieContainer kieContainer;

    public RulePuntuationService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RulePuntuationRequest calculatePuntuation(RulePuntuationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RulePuntuationRequest must not be null.");
        }

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(request);
            int firedRules = kieSession.fireAllRules();
            double scoreSum = sumScores(request);
            logger.info("Drools rules fired: {}", firedRules);
            logger.info("Resulting score sum: {}", scoreSum);
            return request;
        } finally {
            kieSession.dispose();
        }
    }

    public List<AnnotatedFeature> score(List<AnnotatedFeature> features) {
        RulePuntuationRequest request = new RulePuntuationRequest();
        request.setFeatures(features);
        RulePuntuationRequest result = calculatePuntuation(request);
        return result.getFeatures();
    }

    private double sumScores(RulePuntuationRequest request) {
        double sum = 0.0;
        if (request.getFeatures() == null) {
            return sum;
        }

        for (AnnotatedFeature feature : request.getFeatures()) {
            if (feature == null || feature.getAnnotationsByAdducts() == null) {
                continue;
            }
            for (AnnotationsByAdduct byAdduct : feature.getAnnotationsByAdducts()) {
                if (byAdduct == null || byAdduct.getAnnotations() == null) {
                    continue;
                }
                for (Annotation annotation : byAdduct.getAnnotations()) {
                    if (annotation == null || annotation.getScores() == null) {
                        continue;
                    }
                    for (Score score : annotation.getScores()) {
                        if (score != null && score.getValue() != null) {
                            sum += score.getValue();
                        }
                    }
                }
            }
        }

        return sum;
    }
}

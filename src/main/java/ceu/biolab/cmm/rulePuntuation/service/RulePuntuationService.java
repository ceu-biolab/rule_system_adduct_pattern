package ceu.biolab.cmm.rulePuntuation.service;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO;

@Service
public class RulePuntuationService {

    private static final Logger logger = LoggerFactory.getLogger(RulePuntuationService.class);

    private final KieContainer kieContainer;

    public RulePuntuationService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RulePuntuationResponseDTO calculatePuntuation(RulePuntuationRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("RulePuntuationRequestDTO must not be null.");
        }

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(request);
            if (request.getFeature() != null && request.getFeature().getItems() != null) {
                for (var item : request.getFeature().getItems()) {
                    if (item != null) {
                        kieSession.insert(item);
                    }
                }
            }
            
            int firedRules = kieSession.fireAllRules();
            logger.info("Drools rules fired: {}", firedRules);
            //logger.info("Target candidate: {}", request.getTargetCandidate());
            int items = 0;
            if (request.getFeature() != null && request.getFeature().getItems() != null) {
                items = request.getFeature().getItems().size();
            }
            logger.info("Feature items: {}", items);
            logger.info("Score after rules: {}", request.getScore());
            RulePuntuationResponseDTO response = new RulePuntuationResponseDTO();
            response.setScore(request.getScore());
            return response;
        } finally {
            kieSession.dispose();
        }
    }
}

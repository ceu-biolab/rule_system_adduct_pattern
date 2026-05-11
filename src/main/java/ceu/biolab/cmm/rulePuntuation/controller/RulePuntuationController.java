package ceu.biolab.cmm.rulePuntuation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO;
import ceu.biolab.cmm.rulePuntuation.service.RulePuntuationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RulePuntuationController {

    private final RulePuntuationService rulePuntuationService;

    /**
     * Build the controller with its rule punctuation service.
     *
     * @param rulePuntuationService service used to annotate and score features
     */
    public RulePuntuationController(RulePuntuationService rulePuntuationService) {
        this.rulePuntuationService = rulePuntuationService;
    }

    /**
     * Receive the scoring request and return the best-matching annotated feature.
     *
     * @param request rule punctuation input payload
     * @return rule punctuation output payload
     */
    @PostMapping("/rule-puntuation")
    public RulePuntuationResponseDTO score(@Valid @RequestBody RulePuntuationRequestDTO request) {
        return rulePuntuationService.calculatePuntuation(request);
    }
}

package ceu.biolab.cmm.rulePuntuation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequestDTO;
import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationResponseDTO;
import ceu.biolab.cmm.rulePuntuation.service.RulePuntuationService;
import jakarta.validation.Valid;

/**
 * REST adapter for the complete expert-system operation.
 *
 * <p>{@code POST /api/rule-puntuation} first assigns adduct labels to one
 * externally grouped feature and then scores each annotation hypothesis with
 * the Drools rules selected by lipid class and polarity. The historical
 * {@code puntuation} path spelling is retained as part of the public API.</p>
 */
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
     * Annotate and score one grouped chromatographic feature.
     *
     * @param request grouped signals, experimental context and target lipid class
     * @return every surviving annotation hypothesis with its accumulated score
     */
    @PostMapping("/rule-puntuation")
    public RulePuntuationResponseDTO score(@Valid @RequestBody RulePuntuationRequestDTO request) {
        return rulePuntuationService.calculatePuntuation(request);
    }
}

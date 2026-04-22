package ceu.biolab.cmm.rulePuntuation.controller;

import org.springframework.http.ResponseEntity;
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

    public RulePuntuationController(RulePuntuationService rulePuntuationService) {
        this.rulePuntuationService = rulePuntuationService;
    }

    @PostMapping("/rule-puntuation")
    public ResponseEntity<RulePuntuationResponseDTO> score(@Valid @RequestBody RulePuntuationRequestDTO request) {
        RulePuntuationResponseDTO scored = rulePuntuationService.calculatePuntuation(request);
        return ResponseEntity.ok(scored);
    }
}

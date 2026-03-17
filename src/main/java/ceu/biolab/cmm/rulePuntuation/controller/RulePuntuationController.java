package ceu.biolab.cmm.rulePuntuation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.rulePuntuation.dto.RulePuntuationRequest;
import ceu.biolab.cmm.rulePuntuation.service.RulePuntuationService;
import ceu.biolab.cmm.shared.domain.msFeature.AnnotatedFeature;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RulePuntuationController {
    private final RulePuntuationService rulePuntuationService;

    public RulePuntuationController(RulePuntuationService rulePuntuationService) {
        this.rulePuntuationService = rulePuntuationService;
    }

    @PostMapping("/rule-puntuation")
    public ResponseEntity<List<AnnotatedFeature>> score(@Valid @RequestBody RulePuntuationRequest request) {
        List<AnnotatedFeature> scored = rulePuntuationService.score(request.getFeatures());
        return ResponseEntity.ok(scored);
    }
}

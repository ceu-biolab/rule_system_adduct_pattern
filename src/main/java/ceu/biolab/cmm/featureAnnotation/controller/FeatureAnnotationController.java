package ceu.biolab.cmm.featureAnnotation.controller;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequest;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResponse;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeatureAnnotationController {
    private final FeatureAnnotationService featureAnnotationService;

    public FeatureAnnotationController(FeatureAnnotationService featureAnnotationService) {
        this.featureAnnotationService = featureAnnotationService;
    }

    @PostMapping("/annotate-feature")
    public FeatureAnnotationResponse annotateFeature(@Valid @RequestBody FeatureAnnotationRequest request) {
        return featureAnnotationService.annotateFeatures(request);
    }
}

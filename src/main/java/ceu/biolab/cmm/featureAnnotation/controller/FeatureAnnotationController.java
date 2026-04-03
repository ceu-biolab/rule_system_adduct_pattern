package ceu.biolab.cmm.featureAnnotation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationEntryDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class FeatureAnnotationController {
    private final FeatureAnnotationService featureAnnotationService;

    /**
     * Build the controller with its feature annotation service.
     *
     * @param featureAnnotationService service used to transform annotation input
     */
    public FeatureAnnotationController(FeatureAnnotationService featureAnnotationService) {
        this.featureAnnotationService = featureAnnotationService;
    }

    /**
     * Receive the annotation request and return the transformed payload.
     *
     * @param request feature annotation input payload
     * @return feature annotation output payload
     */
    @PostMapping("/annotate-feature")
    public FeatureAnnotationResultDTO annotateFeature(@Valid @RequestBody FeatureAnnotationEntryDTO request) {
        return featureAnnotationService.transform(request);
    }
}

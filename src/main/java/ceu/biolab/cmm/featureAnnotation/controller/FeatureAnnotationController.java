package ceu.biolab.cmm.featureAnnotation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.featureAnnotation.service.FeatureAnnotationService;
import jakarta.validation.Valid;

/**
 * REST adapter for adduct labelling without expert-system scoring.
 *
 * <p>{@code POST /api/annotate-feature} accepts the signals of exactly one
 * feature already grouped by an upstream process such as pyOpenMS.</p>
 */
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
     * Assign polarity-specific adduct labels to one grouped feature.
     *
     * @param request grouped signals, polarity and mass tolerance
     * @return all surviving adduct-labelling hypotheses
     */
    @PostMapping("/annotate-feature")
    public FeatureAnnotationResultDTO annotateFeature(@Valid @RequestBody FeatureAnnotationRequestDTO request) {
        return featureAnnotationService.transform(request);
    }
}

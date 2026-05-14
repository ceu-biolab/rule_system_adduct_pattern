package ceu.biolab.cmm.featureAnnotation.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;

@Service
public class FeatureAnnotationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureAnnotationService.class);

    // Isotope spacing indexed by charge (1–3); index 0 unused
    private static final double[] ISOTOPE_SPACINGS = {0.0, 1.0033, 0.5016, 0.3344};
    private static final double DEFAULT_PPM    = 10.0;
    private static final double DEFAULT_DALTON = 1.0;

    public FeatureAnnotationResultDTO transform(FeatureAnnotationRequestDTO input) {
        int n = input == null || input.getFeatures() == null ? 0 : input.getFeatures().size();
        List<FeatureAnnotation.AnnotatedFeature> candidates = buildCandidates(input);
        LOGGER.info("Generated {} initial hypotheses for {} input peaks", candidates.size(), n);
        FeatureAnnotationResultDTO output = new FeatureAnnotationResultDTO();
        output.getResults().addAll(filter(candidates, n));
        return output;
    }

    public List<FeatureAnnotation.AnnotatedFeature> filter(
            List<FeatureAnnotation.AnnotatedFeature> results, int n) {
        if (results == null || results.isEmpty()) return List.of();
        int threshold = n > 4 ? 3 : 2;
        List<FeatureAnnotation.AnnotatedFeature> passed = results.stream()
                .filter(f -> f.getItems().stream().filter(i -> i.getAdductName() != null).count() >= threshold)
                .collect(Collectors.toList());
        LOGGER.info("Discarded {} hypotheses below match threshold {}", results.size() - passed.size(), threshold);
        return deduplicate(passed);
    }

    // ── annotation pipeline ───────────────────────────────────────────────────

    private List<FeatureAnnotation.AnnotatedFeature> buildCandidates(FeatureAnnotationRequestDTO input) {
        if (input == null || input.getFeatures() == null || input.getFeatures().isEmpty()) return List.of();

        List<FeatureAnnotationRequestDTO.FeatureInput> signals = input.getFeatures().stream()
                .sorted(Comparator.comparingDouble(FeatureAnnotationRequestDTO.FeatureInput::getMzValue))
                .collect(Collectors.toList());

        List<AdductDefinition> adducts = Arrays.stream(IonizationMode.values())
                .flatMap(m -> AdductCatalog.definitionsFor(m).values().stream())
                .collect(Collectors.toList());

        ToleranceMode mode = input.getToleranceMode();
        Double customTolerance = input.getTolerance();
        List<FeatureAnnotation.AnnotatedFeature> candidates = new ArrayList<>();

        for (FeatureAnnotationRequestDTO.FeatureInput signal : signals) {
            int charge = detectCharge(signal, signals, mode, customTolerance);
            for (AdductDefinition sourceAdduct : adducts) {
                if (charge != sourceAdduct.absoluteCharge()) continue;
                candidates.add(buildHypothesis(signal, signals, adducts, mode, customTolerance, sourceAdduct));
            }
        }
        return candidates;
    }

    private FeatureAnnotation.AnnotatedFeature buildHypothesis(
            FeatureAnnotationRequestDTO.FeatureInput source,
            List<FeatureAnnotationRequestDTO.FeatureInput> signals,
            List<AdductDefinition> adducts,
            ToleranceMode mode, Double customTolerance,
            AdductDefinition sourceAdduct) {
        double neutralMass = (source.getMzValue() * sourceAdduct.absoluteCharge() - sourceAdduct.offset())
                / sourceAdduct.multimer();
        LinkedHashSet<FeatureAnnotation.ResultItem> items = new LinkedHashSet<>();
        for (FeatureAnnotationRequestDTO.FeatureInput candidate : signals) {
            String adductName = candidate == source
                    ? sourceAdduct.canonical()
                    : bestAdductFor(candidate, neutralMass, adducts, mode, customTolerance);
            FeatureAnnotation.ResultItem item = new FeatureAnnotation.ResultItem();
            item.setMzValue(candidate.getMzValue());
            item.setIntensity(candidate.getIntensity());
            item.setRetentionTime(candidate.getRetentionTime());
            item.setAdductName(adductName);
            items.add(item);
        }
        FeatureAnnotation.AnnotatedFeature hypothesis = new FeatureAnnotation.AnnotatedFeature();
        hypothesis.setItems(items);
        return hypothesis;
    }

    private String bestAdductFor(FeatureAnnotationRequestDTO.FeatureInput signal, double neutralMass,
                                  List<AdductDefinition> adducts, ToleranceMode mode, Double customTolerance) {
        String best = null;
        double bestDelta = Double.POSITIVE_INFINITY;
        for (AdductDefinition adduct : adducts) {
            double expected = (neutralMass * adduct.multimer() + adduct.offset()) / adduct.absoluteCharge();
            double delta = Math.abs(signal.getMzValue() - expected);
            if (delta <= resolveTolerance(expected, mode, customTolerance) && delta < bestDelta) {
                bestDelta = delta;
                best = adduct.canonical();
            }
        }
        return best;
    }

    private int detectCharge(FeatureAnnotationRequestDTO.FeatureInput signal,
                             List<FeatureAnnotationRequestDTO.FeatureInput> signals,
                             ToleranceMode mode, Double customTolerance) {
        for (int z = 1; z <= 3; z++) {
            double expected = signal.getMzValue() + ISOTOPE_SPACINGS[z];
            for (FeatureAnnotationRequestDTO.FeatureInput other : signals) {
                if (other != signal
                        && Math.abs(other.getMzValue() - expected) <= resolveTolerance(expected, mode, customTolerance)) {
                    return z;
                }
            }
        }
        return 1;
    }

    // ── deduplication ─────────────────────────────────────────────────────────

    private List<FeatureAnnotation.AnnotatedFeature> deduplicate(List<FeatureAnnotation.AnnotatedFeature> results) {
        Map<String, FeatureAnnotation.AnnotatedFeature> unique = new LinkedHashMap<>();
        for (FeatureAnnotation.AnnotatedFeature f : results) unique.putIfAbsent(signature(f), f);
        return new ArrayList<>(unique.values());
    }

    private String signature(FeatureAnnotation.AnnotatedFeature f) {
        return f.getItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::itemKey))
                .map(this::itemKey)
                .collect(Collectors.joining("|"));
    }

    private String itemKey(FeatureAnnotation.ResultItem item) {
        return item.getMzValue() + ":" + item.getIntensity() + ":" + item.getRetentionTime() + ":"
                + (item.getAdductName() == null ? "" : item.getAdductName());
    }

    // ── tolerance ─────────────────────────────────────────────────────────────

    private double resolveTolerance(double mz, ToleranceMode mode, Double custom) {
        double value = custom != null ? custom : (mode == ToleranceMode.PPM ? DEFAULT_PPM : DEFAULT_DALTON);
        return mode == ToleranceMode.PPM ? mz * value / 1_000_000.0 : value;
    }
}

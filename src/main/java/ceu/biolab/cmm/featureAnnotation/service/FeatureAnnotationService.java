package ceu.biolab.cmm.featureAnnotation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequestDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResultDTO;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.ToleranceMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;

@Service
public class FeatureAnnotationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureAnnotationService.class);

    //Constants for isotopic spacing.
    private static final double ISOTOPE_SPACING = 1.0033;
    private static final double HALF_ISOTOPE_SPACING = 0.5016;
    private static final double THIRD_ISOTOPE_SPACING = 0.3344;

    //Tolerance thresholds for matching signals to adduct hypotheses.
    private static final double TOLERANCE_PPM = 10.0;
    private static final double TOLERANCE_DALTON = 1.0;

    /**
     * Transform the input DTO into the output DTO by resolving adduct matches and filtering them.
     *
     * @param input feature annotation input payload
     * @return transformed feature annotation output payload
     */
    public FeatureAnnotationResultDTO transform(FeatureAnnotationRequestDTO input) {
        
        //Generate all candidate annotations based on the input features and configured tolerance.
        FeatureAnnotationResultDTO initial = resolveCombinations(input);
        //Log the total number of input peaks to provide context for the number of generated hypotheses and filtering results.
        int totalInputPeaks = input == null || input.getFeatures() == null ? 0 : input.getFeatures().size();
        
        //Filter the initial hypotheses based on match density and remove duplicates, then log the number of results at each step for traceability.
        LOGGER.info("Generated {} initial hypotheses for {} input peaks", initial.getResults().size(), totalInputPeaks);
        List<FeatureAnnotation.AnnotatedFeature> filtered = filter(new ArrayList<>(initial.getResults()), totalInputPeaks);
        
        //Return the filtered results in the output DTO, preserving the original order from the initial annotation step.
        FeatureAnnotationResultDTO output = new FeatureAnnotationResultDTO();
        output.getResults().addAll(filtered);
        return output;
    }

    /**
     * Filter hypotheses based on match density and remove duplicates.
     *
     * @param results hypotheses to filter
     * @param totalInputPeaks total number of input peaks (N)
     * @return filtered hypotheses
     */
        public List<FeatureAnnotation.AnnotatedFeature> filter(
            List<FeatureAnnotation.AnnotatedFeature> results,
            int totalInputPeaks) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        int threshold = totalInputPeaks > 4 ? 3 : 2;
        List<FeatureAnnotation.AnnotatedFeature> passed = results.stream()
                .filter(result -> countMatches(result) >= threshold)
                .collect(Collectors.toList());

        int discarded = results.size() - passed.size();
        LOGGER.info("Discarded {} hypotheses below match threshold {}", discarded, threshold);

        return deduplicate(passed);
    }

    /**
     * Resolve all plausible adduct combinations for the provided signals.
     *
     * @param input feature annotation input payload
     * @return result DTO with all candidate annotations
     */
    private FeatureAnnotationResultDTO resolveCombinations(FeatureAnnotationRequestDTO input) {
        FeatureAnnotationResultDTO result = new FeatureAnnotationResultDTO();
        
        if (input == null || input.getFeatures() == null || input.getFeatures().isEmpty()) {
            return result;
        }

        List<FeatureAnnotationRequestDTO.FeatureInput> signals = sortSignals(input.getFeatures());
        List<AdductDefinition> adducts = loadAllAdducts();
        Double tolerance = input.getTolerance();

        //Iterate over each signal and adduct definition to build hypothesis groups, then filter out empty groups before returning the result.
        for (FeatureAnnotationRequestDTO.FeatureInput signal : signals) {

            //Get the charge state for the current signal by analyzing isotopic spacing with nearby peaks, which will inform which adducts are plausible for this signal.
            int charge = detectCharge(signal, signals, input.getToleranceMode(), tolerance);

            //For each adduct definition, build a hypothesis group by treating the current signal as the source and looking for matching signals that fit the theoretical mass criteria.
            for (AdductDefinition sourceAdduct : adducts) {
                FeatureAnnotation.AnnotatedFeature group =
                        buildHypothesisGroup(signal, signals, adducts, input.getToleranceMode(), tolerance, charge, sourceAdduct);
                if (group != null && !group.getItems().isEmpty()) {
                    result.getResults().add(group);
                }
            }
        }
        return result;
    }

    /**
     * Count how many items in a hypothesis have an assigned adduct.
     *
     * @param result hypothesis to analyze
     * @return number of matches
     */
    private long countMatches(FeatureAnnotation.AnnotatedFeature result) {
        
        if (result == null || result.getItems() == null) { return 0;}
        
        return result.getItems().stream()
                .filter(item -> item != null && item.getAdductName() != null)
                .count();
    }

    /**
     * Deduplicate hypotheses treating items as an unordered set of feature/adduct pairs.
     *
     * @param results filtered hypotheses
     * @return deduplicated hypotheses
     */
    private List<FeatureAnnotation.AnnotatedFeature> deduplicate(List<FeatureAnnotation.AnnotatedFeature> results) {
        
        Map<String, FeatureAnnotation.AnnotatedFeature> unique = new LinkedHashMap<>();
        
        for (FeatureAnnotation.AnnotatedFeature result : results) {
            String signature = buildSignature(result);
            unique.putIfAbsent(signature, result);
        }

        return new ArrayList<>(unique.values());
    }

    /**
     * Build a stable signature for a hypothesis independent of item ordering.
     *
     * @param result hypothesis to fingerprint
     * @return signature string
     */
    private String buildSignature(FeatureAnnotation.AnnotatedFeature result) {
        
        if (result == null || result.getItems() == null) { return ""; }
        
        return result.getItems().stream()
                .filter(item -> item != null)
                .sorted(Comparator.comparing(this::signaturePart))
                .map(this::signaturePart)
                .collect(Collectors.joining("|"));
    }

    /**
     * Build a signature component for a single result item.
     *
     * @param item result item
     * @return signature component
     */
    private String signaturePart(FeatureAnnotation.ResultItem item) {
        String adduct = item.getAdductName() == null ? "" : item.getAdductName();
        return item.getMzValue() + ":" + item.getIntensity() + ":" + item.getRetentionTime() + ":" + adduct;
    }

    /**
     * Sort input signals by m/z to make matching predictable.
     *
     * @param signals raw input signals
     * @return sorted list of signals
     */
    private List<FeatureAnnotationRequestDTO.FeatureInput> sortSignals(List<FeatureAnnotationRequestDTO.FeatureInput> signals) {
        List<FeatureAnnotationRequestDTO.FeatureInput> sorted = new ArrayList<>(signals);
        sorted.sort(Comparator.comparingDouble(FeatureAnnotationRequestDTO.FeatureInput::getMzValue));
        return sorted;
    }

    /**
     * Build a brute-force hypothesis group for one signal/adduct combination.
     *
     * @param sourceSignal signal used to compute the neutral mass
     * @param signals all available signals
     * @param adducts allowed adduct definitions
     * @param toleranceMode configured tolerance mode
     * @param charge detected charge for the source signal
     * @param sourceAdduct adduct hypothesis for the source signal
     * @return grouped annotations for the hypothesis
     */
    private FeatureAnnotation.AnnotatedFeature buildHypothesisGroup(FeatureAnnotationRequestDTO.FeatureInput sourceSignal,
                                                                             List<FeatureAnnotationRequestDTO.FeatureInput> signals,
                                                                             List<AdductDefinition> adducts,
                                                                             ToleranceMode toleranceMode,
                                                                             Double tolerance,
                                                                             int charge,
                                                                             AdductDefinition sourceAdduct) {
        if (charge != sourceAdduct.absoluteCharge()) {
            return null;
        }

        //Calculate the theoretical neutral mass for the source signal based on its m/z and the adduct hypothesis, which will be used to find matching signals for other adducts.
        double theoreticalMass = calculateTheoreticalMass(sourceSignal.getMzValue(), sourceAdduct);
        FeatureAnnotation.AnnotatedFeature group = new FeatureAnnotation.AnnotatedFeature();
        group.setItems(new java.util.LinkedHashSet<>(buildGroupItems(signals, theoreticalMass, adducts, toleranceMode, tolerance, sourceSignal, sourceAdduct)));
        return group;
    }

    /**
     * Compute theoretical neutral mass based on the adduct used for the hypothesis.
     *
     * @param mz measured mass-to-charge
     * @param adduct adduct definition used as hypothesis
     * @return theoretical neutral mass
     */
    private double calculateTheoreticalMass(double mz, AdductDefinition adduct) {
        return (mz * adduct.absoluteCharge() - adduct.offset()) / adduct.multimer();
    }

    /**
     * Resolve all matching signals for each adduct definition.
     *
     * @param signals all available signals
     * @param theoreticalMass theoretical neutral mass
     * @param adducts allowed adduct definitions
     * @param toleranceMode configured tolerance mode
     * @return list of matching result items
     */
    private List<FeatureAnnotation.ResultItem> buildGroupItems(
            List<FeatureAnnotationRequestDTO.FeatureInput> signals,
            double theoreticalMass,
            List<AdductDefinition> adducts,
            ToleranceMode toleranceMode,
            Double tolerance,
            FeatureAnnotationRequestDTO.FeatureInput sourceSignal,
            AdductDefinition sourceAdduct) {

        List<FeatureAnnotation.ResultItem> items = new ArrayList<>();

        for (FeatureAnnotationRequestDTO.FeatureInput candidate : signals) {
            String adduct = resolveAdductForSignal(candidate, theoreticalMass, adducts, toleranceMode, tolerance, sourceSignal);
            if (candidate == sourceSignal) {
                adduct = sourceAdduct.canonical();
            }
            items.add(toResultItem(candidate, adduct));
        }
        return items;
    }

    /**
     * Resolve the best adduct match for a specific signal within the tolerance.
     *
     * @param signal signal to annotate
     * @param theoreticalMass theoretical neutral mass
     * @param adducts allowed adduct definitions
     * @param toleranceMode configured tolerance mode
     * @return canonical adduct label or null when not matched
     */
    private String resolveAdductForSignal(FeatureAnnotationRequestDTO.FeatureInput signal,
                                          double theoreticalMass,
                                          List<AdductDefinition> adducts,
                                          ToleranceMode toleranceMode,
                                          Double tolerance,
                                          FeatureAnnotationRequestDTO.FeatureInput sourceSignal) {
        String bestAdduct = null;
        double bestDelta = Double.POSITIVE_INFINITY;
        for (AdductDefinition adduct : adducts) {
            double expectedMz = expectedMzFor(theoreticalMass, adduct);
            double tol = resolveTolerance(expectedMz, toleranceMode, tolerance);
            double delta = Math.abs(signal.getMzValue() - expectedMz);
            if (delta <= tol && delta < bestDelta) {
                bestDelta = delta;
                bestAdduct = adduct.canonical();
            }
        }
        return bestAdduct;
    }

    /**
     * Compute the expected m/z for a theoretical mass and adduct definition.
     *
     * @param theoreticalMass theoretical neutral mass
     * @param adduct adduct definition
     * @return expected mass-to-charge value
     */
    private double expectedMzFor(double theoreticalMass, AdductDefinition adduct) {
        return (theoreticalMass * adduct.multimer() + adduct.offset()) / adduct.absoluteCharge();
    }


    /**
     * Detect the charge state for a signal by checking isotopic spacing against nearby peaks.
     *
     * @param signal target signal to inspect
     * @param signals full list of signals to compare against
     * @param toleranceMode tolerance mode used for matching
     * @return detected charge state (1, 2, or 3)
     */
    private int detectCharge(FeatureAnnotationRequestDTO.FeatureInput signal,
                             List<FeatureAnnotationRequestDTO.FeatureInput> signals,
                             ToleranceMode toleranceMode,
                             Double tolerance) {

        for (int charge = 1; charge <= 3; charge++) {
            double spacing = isotopeSpacingForCharge(charge);
            double expected = signal.getMzValue() + spacing;
            for (FeatureAnnotationRequestDTO.FeatureInput candidate : signals) {
                if (candidate == signal) {
                    continue;
                }
                if (Math.abs(candidate.getMzValue() - expected) <= resolveTolerance(expected, toleranceMode, tolerance)) {
                    return charge;
                }
            }
        }
        return 1;
    }

    /**
     * Return expected isotopic spacing for a given charge state.
     *
     * @param charge charge state
     * @return isotopic spacing in m/z units
     */
    private double isotopeSpacingForCharge(int charge) {
        if (charge == 2) {
            return HALF_ISOTOPE_SPACING;
        }
        if (charge == 3) {
            return THIRD_ISOTOPE_SPACING;
        }
        return ISOTOPE_SPACING;
    }

    /**
    * Load all adduct definitions from resources.
     *
     * @return list of allowed adduct definitions
     */
    private List<AdductDefinition> loadAllAdducts() {
        
        List<AdductDefinition> allowed = new ArrayList<>();
        
        for (IonizationMode mode : IonizationMode.values()) {
            for (AdductDefinition definition : AdductCatalog.definitionsFor(mode).values()) {
                allowed.add(definition);
            }
        }
        return allowed;
    }

    /**
     * Resolve the matching tolerance for a specific m/z based on the configured mode.
     *
     * @param mz target mass-to-charge to evaluate
     * @param mode tolerance mode (PPM or DALTON)
     * @return absolute tolerance in Daltons
     */
    private double resolveTolerance(double mz, ToleranceMode mode, Double customTolerance) {
        double value = customTolerance != null ? customTolerance : (mode == ToleranceMode.PPM ? TOLERANCE_PPM : TOLERANCE_DALTON);
        if (mode == ToleranceMode.PPM) {
            return mz * value / 1_000_000.0;
        }
        return value;
    }


    /**
     * Convert a matched signal into a result item with the assigned adduct label.
     *
     * @param input matched signal
     * @param adduct assigned adduct label
     * @return result item
     */
    private FeatureAnnotation.ResultItem toResultItem(FeatureAnnotationRequestDTO.FeatureInput input,
                                                      String adduct) {
        FeatureAnnotation.ResultItem item = new FeatureAnnotation.ResultItem();
        item.setMzValue(input.getMzValue());
        item.setIntensity(input.getIntensity());
        item.setRetentionTime(input.getRetentionTime());
        item.setAdductName(adduct);
        return item;
    }
}

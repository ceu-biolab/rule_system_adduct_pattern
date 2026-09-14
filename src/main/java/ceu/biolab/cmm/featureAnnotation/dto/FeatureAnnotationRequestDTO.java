package ceu.biolab.cmm.featureAnnotation.dto;

import java.util.ArrayList;
import java.util.List;

import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.ToleranceMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request for assigning adduct labels to the signals of one pre-grouped
 * chromatographic feature.
 *
 * <p>The caller, normally the pyOpenMS preprocessing pipeline, is responsible
 * for deconvolution and grouping. The service does not group signals by
 * retention time. {@link #ionizationMode} restricts candidate labels to the
 * acquisition polarity.</p>
 */
@Data
public class FeatureAnnotationRequestDTO {
    /** Signals belonging to exactly one deconvoluted feature group. */
    @NotEmpty
    @Valid
    private List<FeatureInput> features = new ArrayList<>();

    /** Acquisition polarity; only definitions for this mode are considered. */
    @NotNull
    private IonizationMode ionizationMode;

    /** Unit used to interpret {@link #tolerance}. */
    @NotNull
    private ToleranceMode toleranceMode;

    /** Optional matching tolerance; defaults are selected by {@code toleranceMode}. */
    private Double tolerance;

    /** A single signal supplied by the external feature-grouping pipeline. */
    @Data
    public static class FeatureInput {
        /** Measured mass-to-charge value. */
        @NotNull
        @JsonProperty("mzValue")
        private Double mzValue;

        /** Signal intensity used by the expert-system ordering rules. */
        @NotNull
        @JsonProperty("intensity")
        private Double intensity;

        /** Retention time carried into the result; not used to regroup signals. */
        @NotNull
        @JsonProperty("retentionTime")
        private Double retentionTime;
    }
}

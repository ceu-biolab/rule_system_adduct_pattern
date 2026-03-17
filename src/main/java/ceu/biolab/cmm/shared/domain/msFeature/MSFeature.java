package ceu.biolab.cmm.shared.domain.msFeature;

import lombok.Data;

@Data
public class MSFeature implements IMSFeature {
    private double mzValue;
    private Double intensity;

    public MSFeature() {
        this.mzValue = 0.0;
        this.intensity = null;
    }

    public MSFeature(double mzValue) {
        this.mzValue = mzValue;
        this.intensity = null;
    }

    public MSFeature(double mzValue, Double intensity) {
        this.mzValue = mzValue;
        this.intensity = intensity;
    }
}

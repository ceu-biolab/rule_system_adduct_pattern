package io.github.mzmine.datamodel;

public class FoundAdduct {
    private String adductName;
    private double intensity;

    public FoundAdduct() {
    }

    public String getAdductName() {
        return adductName;
    }

    public void setAdductName(String adductName) {
        this.adductName = adductName;
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }
}
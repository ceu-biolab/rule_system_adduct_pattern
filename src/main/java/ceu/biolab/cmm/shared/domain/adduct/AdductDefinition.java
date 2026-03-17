package ceu.biolab.cmm.shared.domain.adduct;

import ceu.biolab.cmm.shared.domain.IonizationMode;

import java.util.Objects;

public final class AdductDefinition {
    private final String canonical;
    private final IonizationMode ionizationMode;
    private final int multimer;
    private final String descriptor;
    private final int charge;
    private final double offset;

    AdductDefinition(String canonical,
                     IonizationMode ionizationMode,
                     int multimer,
                     String descriptor,
                     int charge,
                     double offset) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.ionizationMode = Objects.requireNonNull(ionizationMode, "ionizationMode");
        this.multimer = multimer;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.charge = charge;
        this.offset = offset;
    }

    public String canonical() {
        return canonical;
    }

    public IonizationMode ionizationMode() {
        return ionizationMode;
    }

    public int multimer() {
        return multimer;
    }

    public String descriptor() {
        return descriptor;
    }

    public int charge() {
        return charge;
    }

    public int absoluteCharge() {
        return Math.abs(charge);
    }

    public double offset() {
        return offset;
    }

    public boolean isPositive() {
        return charge > 0;
    }

    public boolean isNegative() {
        return charge < 0;
    }

    @Override
    public String toString() {
        return canonical;
    }
}

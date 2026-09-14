package ceu.biolab.cmm.shared.domain.adduct;

import ceu.biolab.cmm.shared.domain.IonizationMode;

import java.util.Objects;

/**
 * Immutable description of an ion label that can be assigned to an observed
 * LC-MS signal.
 *
 * <p>Most definitions are relative to a neutral molecular mass. For those
 * definitions, {@code massValue} is the signed ion offset in daltons and the
 * observed m/z is calculated from the neutral mass, multimer and charge. Fixed
 * diagnostic ions instead store their absolute expected m/z and are never used
 * as the source of a neutral-mass hypothesis.</p>
 */
public final class AdductDefinition {
    /** Describes how the stored mass value is interpreted. */
    public enum MassType {
        /** A signed mass offset relative to one or more neutral molecules. */
        NEUTRAL_MASS_OFFSET,
        /** An absolute diagnostic m/z independent of the neutral molecule. */
        FIXED_MZ
    }

    private final String canonical;
    private final IonizationMode ionizationMode;
    private final int multimer;
    private final String descriptor;
    private final int charge;
    private final double massValue;
    private final MassType massType;

    AdductDefinition(String canonical,
                     IonizationMode ionizationMode,
                     int multimer,
                     String descriptor,
                     int charge,
                     double massValue,
                     MassType massType) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.ionizationMode = Objects.requireNonNull(ionizationMode, "ionizationMode");
        this.multimer = multimer;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.charge = charge;
        this.massValue = massValue;
        this.massType = Objects.requireNonNull(massType, "massType");
    }

    /** @return exact label expected by the Drools decision tables */
    public String canonical() {
        return canonical;
    }

    /** @return acquisition polarity in which this ion may occur */
    public IonizationMode ionizationMode() {
        return ionizationMode;
    }

    /** @return number of neutral molecules represented by the ion */
    public int multimer() {
        return multimer;
    }

    /** @return textual part following {@code M} in the canonical label */
    public String descriptor() {
        return descriptor;
    }

    /** @return signed charge of the ion */
    public int charge() {
        return charge;
    }

    /** @return absolute charge magnitude */
    public int absoluteCharge() {
        return Math.abs(charge);
    }

    /**
     * Returns the signed neutral-mass offset for ordinary adducts, or the
     * absolute m/z for fixed diagnostic ions.
     *
     * @return mass value interpreted according to {@link #massType()}
     */
    public double offset() {
        return massValue;
    }

    /** @return interpretation of {@link #offset()} */
    public MassType massType() {
        return massType;
    }

    /** @return whether this ion can establish a neutral-mass hypothesis */
    public boolean canInferNeutralMass() {
        return massType == MassType.NEUTRAL_MASS_OFFSET;
    }

    /**
     * Calculate the neutral mass represented by an observed m/z.
     *
     * @param observedMz measured mass-to-charge value
     * @return inferred neutral molecular mass
     * @throws IllegalStateException if this is a fixed diagnostic ion
     */
    public double neutralMassFrom(double observedMz) {
        if (!canInferNeutralMass()) {
            throw new IllegalStateException("A fixed diagnostic ion cannot infer neutral mass: " + canonical);
        }
        return (observedMz * absoluteCharge() - massValue) / multimer;
    }

    /**
     * Calculate the expected m/z for this ion.
     *
     * @param neutralMass neutral molecular mass; ignored for fixed diagnostic ions
     * @return expected mass-to-charge value
     */
    public double expectedMz(double neutralMass) {
        if (massType == MassType.FIXED_MZ) {
            return massValue;
        }
        return (neutralMass * multimer + massValue) / absoluteCharge();
    }

    /** @return {@code true} for a positively charged ion */
    public boolean isPositive() {
        return charge > 0;
    }

    /** @return {@code true} for a negatively charged ion */
    public boolean isNegative() {
        return charge < 0;
    }

    @Override
    public String toString() {
        return canonical;
    }
}

package ceu.biolab.cmm.shared.service.adduct;

import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AdductService {
    private AdductService() {
    }

    public static AdductDefinition requireDefinition(IonizationMode ionizationMode, String canonicalAdduct) {
        if (ionizationMode == null) {
            throw new IllegalArgumentException("Ionization mode is required");
        }
        if (canonicalAdduct == null || canonicalAdduct.isBlank()) {
            throw new IllegalArgumentException("Adduct is required and must be in canonical format");
        }
        String normalized = canonicalAdduct.trim();
        Map<String, AdductDefinition> definitions = AdductCatalog.definitionsFor(ionizationMode);
        AdductDefinition definition = definitions.get(normalized);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported adduct '" + canonicalAdduct + "' for ionization mode " + ionizationMode);
        }
        return definition;
    }

    public static Set<String> availableAdducts(IonizationMode ionizationMode) {
        return AdductCatalog.definitionsFor(ionizationMode).keySet();
    }

    public static double neutralMassFromMz(double mz, AdductDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return (mz * definition.absoluteCharge() - definition.offset()) / definition.multimer();
    }
}

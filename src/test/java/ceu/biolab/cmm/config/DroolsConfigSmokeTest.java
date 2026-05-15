package ceu.biolab.cmm.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.kie.api.runtime.KieSession;

import ceu.biolab.cmm.shared.domain.MobilePhases;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation;
import ceu.biolab.cmm.shared.dto.FeatureAnnotation.ResultItem;

class DroolsConfigSmokeTest {

    @Test
    void loadsAndFiresAllRulesWithoutCompilationErrors() {
        KieContainerProvider provider = new KieContainerProvider();
        FeatureAnnotation.AnnotatedFeature lipid = new FeatureAnnotation.AnnotatedFeature();
        List<MobilePhases> mobilePhases = new ArrayList<>(List.of(MobilePhases.values()));

        KieSession kieSession = provider.getContainer().newKieSession();
        try {
            kieSession.setGlobal("lipid", lipid);
            kieSession.setGlobal("mobilePhases", mobilePhases);
            kieSession.setGlobal("sampleType", "PLASMA");

            kieSession.insert(resultItem("[M+H]+", 1000.0));
            kieSession.insert(resultItem("[M+Na]+", 900.0));
            kieSession.insert(resultItem("[M+K]+", 800.0));
            kieSession.insert(resultItem("[M+NH4]+", 700.0));
            kieSession.insert(resultItem("[M+C2H7N2]+", 600.0));
            kieSession.insert(resultItem("[M-H]-", 500.0));
            kieSession.insert(resultItem("[M+CH3COO]-", 950.0));
            kieSession.insert(resultItem("[M+HCOO]-", 850.0));
            kieSession.insert(resultItem("[M+Cl]-", 750.0));
            kieSession.insert(resultItem("[M+CH3COO+(CH3COONa)]-", 650.0));
            kieSession.insert(resultItem("[M+CH3COO+(CH3COONa)2]-", 550.0));
            kieSession.insert(resultItem("[M+CH3COO+(CH3COONa)3]-", 450.0));
            kieSession.insert(resultItem("[M+HCOO+(CH3COONa)]-", 350.0));

            ThrowingSupplier<Integer> fireAllRules = kieSession::fireAllRules;
            int firedRules = assertDoesNotThrow(fireAllRules);
            assertTrue(firedRules > 0);
        } finally {
            kieSession.dispose();
        }
    }

    private ResultItem resultItem(String adductName, double intensity) {
        ResultItem item = new ResultItem();
        item.setMzValue(200.0);
        item.setIntensity(intensity);
        item.setRetentionTime(1.0);
        item.setAdductName(adductName);
        return item;
    }
}
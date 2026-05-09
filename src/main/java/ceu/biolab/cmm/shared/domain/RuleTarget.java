package ceu.biolab.cmm.shared.domain;

/**
 * Lipid class targets derived from DRL rule file prefixes.
 * Each constant maps to the exact prefix used in the DRL file name:
 * {@code {drlPrefix}_{Positive|Negative}Check.drl}.
 */
public enum RuleTarget {
    CAR("CAR"),
    CE("CE"),
    CER("Cer"),
    CHOL("Chol"),
    DG("DG"),
    FA("FA"),
    HEXCER("HexCer"),
    LPC("LPC"),
    LPC_OP("LPC_OP"),
    LPE("LPE"),
    LPE_OP("LPE_OP"),
    LPI("LPI"),
    PC("PC"),
    PC_OP("PC_OP"),
    PE("PE"),
    PE_OP("PE_OP"),
    PI("PI"),
    SM("SM"),
    TG("TG");

    private final String drlPrefix;

    RuleTarget(String drlPrefix) {
        this.drlPrefix = drlPrefix;
    }

    /** Returns the prefix as written in the DRL file name (e.g. "Cer", "HexCer", "LPC_OP"). */
    public String getDrlPrefix() {
        return drlPrefix;
    }
}

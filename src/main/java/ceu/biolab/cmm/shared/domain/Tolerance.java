package ceu.biolab.cmm.shared.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Pairs a numeric tolerance value with its unit (PPM or Dalton). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tolerance {
    private double value;
    private ToleranceMode mode;
}

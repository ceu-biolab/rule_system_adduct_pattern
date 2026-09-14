package ceu.biolab.cmm.shared.domain.adduct;

import ceu.biolab.cmm.shared.domain.IonizationMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Polarity-specific catalog of adduct and diagnostic-ion definitions used by
 * feature annotation.
 *
 * <p>Definitions are loaded from classpath CSV resources. Their canonical names
 * deliberately match the string literals in the Drools decision tables. The
 * catalog preserves CSV order so that, when two aliases have the same mass,
 * the rule-facing canonical label placed first in the file wins a tie.</p>
 */
public final class AdductCatalog {
    private static final Pattern CANONICAL_PATTERN =
            Pattern.compile("^\\[(?<body>[^]]+)](?:(?<chargeDigits>\\d+)?(?<sign>[+-]))$");
    private static final Pattern BODY_PATTERN =
            Pattern.compile("^(?<multimer>\\d*)M(?<descriptor>.*)$");

    private static final Map<IonizationMode, Map<String, AdductDefinition>> DEFINITIONS;

    static {
        Map<IonizationMode, Map<String, AdductDefinition>> byMode = new EnumMap<>(IonizationMode.class);
        Map<String, AdductDefinition> positive = new LinkedHashMap<>(
                load("/adducts/adducts_positive_mode.csv", IonizationMode.POSITIVE));
        positive.putAll(loadFixedMz("/adducts/diagnostic_ions_positive_mode.csv", IonizationMode.POSITIVE));
        byMode.put(IonizationMode.POSITIVE, Collections.unmodifiableMap(positive));
        byMode.put(IonizationMode.NEGATIVE, load("/adducts/adducts_negative_mode.csv", IonizationMode.NEGATIVE));
        DEFINITIONS = Collections.unmodifiableMap(byMode);
    }

    private AdductCatalog() {
    }

    /**
     * Get all definitions allowed for one acquisition polarity.
     *
     * @param ionizationMode positive or negative acquisition mode
     * @return immutable, insertion-ordered map keyed by canonical label
     */
    public static Map<String, AdductDefinition> definitionsFor(IonizationMode ionizationMode) {
        Map<String, AdductDefinition> definitions = DEFINITIONS.get(ionizationMode);
        if (definitions == null) {
            return Collections.emptyMap();
        }
        return definitions;
    }

    private static Map<String, AdductDefinition> load(String resourcePath, IonizationMode ionizationMode) {
        try (InputStream stream = AdductCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing adduct resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .skip(1)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(line -> parseLine(line, ionizationMode))
                        .collect(Collectors.collectingAndThen(
                                Collectors.toMap(
                                        AdductDefinition::canonical,
                                        Function.identity(),
                                        (first, duplicate) -> first,
                                        LinkedHashMap::new),
                                Collections::unmodifiableMap));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load adduct definitions from " + resourcePath, e);
        }
    }

    private static AdductDefinition parseLine(String csvLine, IonizationMode ionizationMode) {
        String[] parts = splitCsvLine(csvLine);
        if (parts.length != 2) {
            throw new IllegalStateException("Unexpected adduct CSV format: " + csvLine);
        }
        String canonicalRaw = parts[0].trim();
        if (canonicalRaw.startsWith("\"") && canonicalRaw.endsWith("\"") && canonicalRaw.length() >= 2) {
            canonicalRaw = canonicalRaw.substring(1, canonicalRaw.length() - 1);
        }
        String canonical = canonicalRaw.trim();
        double offset;
        try {
            offset = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid adduct mass in CSV: " + csvLine, e);
        }

        Matcher canonicalMatcher = CANONICAL_PATTERN.matcher(canonical);
        if (!canonicalMatcher.matches()) {
            throw new IllegalStateException("Adduct does not follow canonical format [nM+X]z±: " + canonical);
        }
        String body = canonicalMatcher.group("body");
        String chargeDigits = canonicalMatcher.group("chargeDigits");
        String sign = canonicalMatcher.group("sign");

        Matcher bodyMatcher = BODY_PATTERN.matcher(body);
        if (!bodyMatcher.matches()) {
            throw new IllegalStateException("Unable to parse canonical adduct body: " + canonical);
        }
        String multimerDigits = bodyMatcher.group("multimer");
        String descriptor = bodyMatcher.group("descriptor");
        int multimer = multimerDigits == null || multimerDigits.isBlank() ? 1 : Integer.parseInt(multimerDigits);

        int chargeMagnitude = chargeDigits == null || chargeDigits.isBlank() ? 1 : Integer.parseInt(chargeDigits);
        int signValue = Objects.equals("+", sign) ? 1 : -1;
        int charge = chargeMagnitude * signValue;

        validateChargeAgainstMode(canonical, ionizationMode, charge);

        return new AdductDefinition(
                canonical,
                ionizationMode,
                multimer,
                descriptor == null ? "" : descriptor,
                charge,
                offset,
                AdductDefinition.MassType.NEUTRAL_MASS_OFFSET);
    }

    private static Map<String, AdductDefinition> loadFixedMz(
            String resourcePath, IonizationMode ionizationMode) {
        try (InputStream stream = AdductCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing diagnostic-ion resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .skip(1)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(line -> parseFixedMzLine(line, ionizationMode))
                        .collect(Collectors.collectingAndThen(
                                Collectors.toMap(
                                        AdductDefinition::canonical,
                                        Function.identity(),
                                        (first, duplicate) -> first,
                                        LinkedHashMap::new),
                                Collections::unmodifiableMap));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load diagnostic ions from " + resourcePath, e);
        }
    }

    private static AdductDefinition parseFixedMzLine(String csvLine, IonizationMode ionizationMode) {
        String[] parts = splitCsvLine(csvLine);
        if (parts.length != 2) {
            throw new IllegalStateException("Unexpected diagnostic-ion CSV format: " + csvLine);
        }
        String canonical = unquote(parts[0]);
        double fixedMz;
        try {
            fixedMz = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid diagnostic-ion m/z in CSV: " + csvLine, e);
        }

        Matcher canonicalMatcher = CANONICAL_PATTERN.matcher(canonical);
        if (!canonicalMatcher.matches()) {
            throw new IllegalStateException("Diagnostic ion does not follow canonical charged-ion format: " + canonical);
        }
        String chargeDigits = canonicalMatcher.group("chargeDigits");
        String sign = canonicalMatcher.group("sign");
        int chargeMagnitude = chargeDigits == null || chargeDigits.isBlank() ? 1 : Integer.parseInt(chargeDigits);
        int charge = chargeMagnitude * (Objects.equals("+", sign) ? 1 : -1);
        validateChargeAgainstMode(canonical, ionizationMode, charge);

        return new AdductDefinition(
                canonical,
                ionizationMode,
                1,
                canonicalMatcher.group("body"),
                charge,
                fixedMz,
                AdductDefinition.MassType.FIXED_MZ);
    }

    private static void validateChargeAgainstMode(String canonical, IonizationMode ionizationMode, int charge) {
        if (ionizationMode == IonizationMode.POSITIVE && charge <= 0) {
            throw new IllegalStateException("Positive-mode adduct must have a positive charge: " + canonical);
        }
        if (ionizationMode == IonizationMode.NEGATIVE && charge >= 0) {
            throw new IllegalStateException("Negative-mode adduct must have a negative charge: " + canonical);
        }
    }

    private static String[] splitCsvLine(String csvLine) {
        int commaIndex = csvLine.indexOf(',');
        if (commaIndex < 0) {
            return new String[]{csvLine};
        }
        String first = csvLine.substring(0, commaIndex);
        String second = commaIndex + 1 < csvLine.length() ? csvLine.substring(commaIndex + 1) : "";
        return new String[]{first, second};
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}

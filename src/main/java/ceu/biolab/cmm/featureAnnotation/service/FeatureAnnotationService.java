package ceu.biolab.cmm.featureAnnotation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ceu.biolab.cmm.featureAnnotation.dto.AnnotatedFeatureDTO;
import ceu.biolab.cmm.featureAnnotation.dto.AnnotatedFeatureGroupDTO;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationRequest;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureAnnotationResponse;
import ceu.biolab.cmm.featureAnnotation.dto.FeatureDTO;
import ceu.biolab.cmm.shared.domain.IonizationMode;
import ceu.biolab.cmm.shared.domain.adduct.AdductCatalog;
import ceu.biolab.cmm.shared.domain.adduct.AdductDefinition;

@Service
public class FeatureAnnotationService {
    private static final double MIN_MATCH_RATIO = 0.8;

    public FeatureAnnotationResponse annotateFeatures(FeatureAnnotationRequest request) {
        if (request == null || request.getFeatures() == null || request.getFeatures().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature list is required.");
        }

        double tolerance = request.getTolerance() == null ? 0.0 : request.getTolerance();
        List<AdductEntry> entries = loadAdductEntries();
        entries.sort(Comparator.comparingDouble(entry -> entry.mass));

        List<FeatureEntry> featureEntries = new ArrayList<>();
        List<GroupBucket> buckets = new ArrayList<>();
        int index = 0;
        for (FeatureDTO feature : request.getFeatures()) {
            if (feature != null && feature.getMzValue() != null) {
                featureEntries.add(new FeatureEntry(feature.getMzValue(), feature, index));
            }
            index++;
        }

        featureEntries.sort(Comparator.comparingDouble(entry -> entry.mzValue));

        int minFeatures = (int) Math.ceil(featureEntries.size() * MIN_MATCH_RATIO);
        for (FeatureEntry sourceFeature : featureEntries) {
            for (AdductEntry source : entries) {
                double candidateMass = (sourceFeature.mzValue * source.charge - source.mass) / source.multimer;
                List<FeatureDTO> matched = findMatchingFeatures(candidateMass, tolerance, featureEntries, sourceFeature.originalIndex);
                if (matched.isEmpty()) {
                    continue;
                }
                GroupBucket bucket = findOrCreateBucket(buckets, candidateMass, tolerance);
                addAnnotatedFeature(bucket, sourceFeature.feature, source.canonical);
                for (FeatureDTO match : matched) {
                    addAnnotatedFeature(bucket, match, source.canonical);
                }
            }
        }

        List<AnnotatedFeatureGroupDTO> results = new ArrayList<>();
        for (GroupBucket bucket : buckets) {
            results.add(bucket.group);
        }
        results = filter(results, minFeatures);
        FeatureAnnotationResponse response = new FeatureAnnotationResponse();
        response.setAnnotatedFeatures(results);
        return response;
    }

    private List<FeatureDTO> findMatchingFeatures(double candidateMass,
                                                  double tolerance,
                                                  List<FeatureEntry> features,
                                                  int sourceIndex) {
        List<FeatureDTO> matches = new ArrayList<>();
        if (features.isEmpty()) {
            return matches;
        }
        double min = candidateMass - tolerance;
        double max = candidateMass + tolerance;

        int low = 0;
        int high = features.size();
        while (low < high) {
            int mid = (low + high) / 2;
            if (features.get(mid).mzValue < min) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int idx = low;
        Set<FeatureDTO> unique = new LinkedHashSet<>();
        while (idx < features.size()) {
            FeatureEntry entry = features.get(idx);
            if (entry.mzValue > max) {
                break;
            }
            if (entry.originalIndex != sourceIndex) {
                unique.add(copyFeature(entry.feature));
            }
            idx++;
        }
        matches.addAll(unique);
        return matches;
    }

    private List<AdductEntry> loadAdductEntries() {
        List<AdductEntry> entries = new ArrayList<>();
        for (IonizationMode mode : IonizationMode.values()) {
            for (AdductDefinition definition : AdductCatalog.definitionsFor(mode).values()) {
                entries.add(new AdductEntry(
                        definition.canonical(),
                        definition.offset(),
                        definition.absoluteCharge(),
                        definition.multimer()));
            }
        }
        return entries;
    }

    private FeatureDTO copyFeature(FeatureDTO source) {
        if (source == null) {
            return null;
        }
        FeatureDTO copy = new FeatureDTO();
        copy.setMzValue(source.getMzValue());
        copy.setIntensity(source.getIntensity());
        copy.setRetentionTime(source.getRetentionTime());
        return copy;
    }

    private List<AnnotatedFeatureGroupDTO> filter(List<AnnotatedFeatureGroupDTO> input, int minFeatures) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        List<AnnotatedFeatureGroupDTO> filtered = new ArrayList<>();
        for (AnnotatedFeatureGroupDTO group : input) {
            if (group == null || group.getAnnotatedFeatures() == null) {
                continue;
            }
            int uniqueCount = countUniqueFeatures(group);
            if (uniqueCount > 1 && uniqueCount >= minFeatures) {
                filtered.add(group);
            }
        }
        return filtered;
    }

    private int countUniqueFeatures(AnnotatedFeatureGroupDTO group) {
        Set<String> keys = new LinkedHashSet<>();
        for (AnnotatedFeatureDTO feature : group.getAnnotatedFeatures()) {
            if (feature == null) {
                continue;
            }
            keys.add(buildFeatureKey(feature));
        }
        return keys.size();
    }

    private String buildFeatureKey(AnnotatedFeatureDTO feature) {
        return String.valueOf(feature.getMzValue())
                + "|" + String.valueOf(feature.getIntensity())
                + "|" + String.valueOf(feature.getRetentionTime());
    }

    private void addAnnotatedFeature(GroupBucket bucket,
                                     FeatureDTO feature,
                                     String adduct) {
        if (feature == null) {
            return;
        }
        AnnotatedFeatureDTO output = new AnnotatedFeatureDTO();
        output.setMzValue(feature.getMzValue());
        output.setIntensity(feature.getIntensity());
        output.setRetentionTime(feature.getRetentionTime());
        output.setAdduct(adduct);
        String key = buildFeatureKey(output) + "|" + String.valueOf(adduct);
        if (!bucket.uniqueKeys.add(key)) {
            return;
        }
        bucket.group.getAnnotatedFeatures().add(output);
    }

    private GroupBucket findOrCreateBucket(List<GroupBucket> buckets, double candidateMass, double tolerance) {
        for (GroupBucket bucket : buckets) {
            if (Math.abs(bucket.mass - candidateMass) <= tolerance) {
                return bucket;
            }
        }
        GroupBucket created = new GroupBucket(candidateMass, new AnnotatedFeatureGroupDTO());
        buckets.add(created);
        return created;
    }

    private static final class AdductEntry {
        private final String canonical;
        private final double mass;
        private final int charge;
        private final int multimer;

        private AdductEntry(String canonical, double mass, int charge, int multimer) {
            this.canonical = canonical;
            this.mass = mass;
            this.charge = charge == 0 ? 1 : charge;
            this.multimer = multimer <= 0 ? 1 : multimer;
        }
    }

    private static final class FeatureEntry {
        private final double mzValue;
        private final FeatureDTO feature;
        private final int originalIndex;

        private FeatureEntry(double mzValue, FeatureDTO feature, int originalIndex) {
            this.mzValue = mzValue;
            this.feature = feature;
            this.originalIndex = originalIndex;
        }
    }

    private static final class GroupBucket {
        private final double mass;
        private final AnnotatedFeatureGroupDTO group;
        private final Set<String> uniqueKeys = new LinkedHashSet<>();

        private GroupBucket(double mass, AnnotatedFeatureGroupDTO group) {
            this.mass = mass;
            this.group = group;
        }
    }
}

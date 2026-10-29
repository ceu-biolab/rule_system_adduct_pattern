"""pyOpenMS mass-trace detection and isotope-aware ion feature construction."""

from __future__ import annotations

from bisect import bisect_left, bisect_right
from pathlib import Path
from typing import Iterable

import numpy as np
import pyopenms as oms

from lipid_rule_eval.config import PreprocessingConfig
from lipid_rule_eval.models import InputFile, IonFeature


def _set_parameter(params: oms.Param, name: str, value: object) -> None:
    """Set an OpenMS parameter with a useful error if the key changes."""
    key = name.encode()
    if key not in params.asDict():
        raise KeyError(f"pyOpenMS parameter {name!r} is unavailable")
    params.setValue(name, value)


def _load_ms1(path: Path) -> oms.MSExperiment:
    options = oms.PeakFileOptions()
    options.setMSLevels([1])
    loader = oms.MzMLFile()
    loader.setOptions(options)
    experiment = oms.MSExperiment()
    loader.load(str(path), experiment)
    experiment.sortSpectra(True)
    if experiment.getNrSpectra() == 0:
        raise ValueError(f"No MS1 spectra found in {path}")
    return experiment


def _ensure_centroided(experiment: oms.MSExperiment, path: Path) -> oms.MSExperiment:
    spectrum_types = {int(spectrum.getType()) for spectrum in experiment}
    profile = int(oms.SpectrumSettings.SpectrumType.PROFILE)
    centroid = int(oms.SpectrumSettings.SpectrumType.CENTROID)
    unknown = int(oms.SpectrumSettings.SpectrumType.UNKNOWN)
    if spectrum_types == {profile}:
        picked = oms.MSExperiment()
        oms.PeakPickerHiRes().pickExperiment(experiment, picked, True)
        return picked
    if profile in spectrum_types:
        raise ValueError(f"Mixed profile and centroid MS1 spectra are unsupported: {path}")
    if spectrum_types - {centroid, unknown}:
        raise ValueError(f"Unexpected MS1 spectrum type(s) {spectrum_types}: {path}")
    return experiment


def _detect_mass_traces(
    experiment: oms.MSExperiment, config: PreprocessingConfig
) -> list[oms.Kernel_MassTrace]:
    traces: list[oms.Kernel_MassTrace] = []
    detector = oms.MassTraceDetection()
    params = detector.getDefaults()
    _set_parameter(params, "mass_error_ppm", config.mass_error_ppm)
    _set_parameter(params, "noise_threshold_int", config.noise_threshold_int)
    _set_parameter(params, "chrom_peak_snr", config.chrom_peak_snr)
    _set_parameter(params, "min_trace_length", config.min_trace_length_seconds)
    _set_parameter(params, "quant_method", "area")
    detector.setParameters(params)
    detector.run(experiment, traces, 0)

    split: list[oms.Kernel_MassTrace] = []
    peak_detector = oms.ElutionPeakDetection()
    peak_params = peak_detector.getDefaults()
    _set_parameter(peak_params, "chrom_peak_snr", config.chrom_peak_snr)
    _set_parameter(peak_params, "width_filtering", "fixed")
    peak_detector.setParameters(peak_params)
    peak_detector.detectPeaks(traces, split)
    return split


def _find_isotope_features(
    traces: list[oms.Kernel_MassTrace], config: PreprocessingConfig
) -> oms.FeatureMap:
    feature_map = oms.FeatureMap()
    chromatograms: list[oms.MSChromatogram] = []
    finder = oms.FeatureFindingMetabo()
    params = finder.getDefaults()
    _set_parameter(params, "charge_lower_bound", config.charge_lower_bound)
    _set_parameter(params, "charge_upper_bound", config.charge_upper_bound)
    _set_parameter(params, "isotope_filtering_model", config.isotope_filtering_model)
    _set_parameter(params, "remove_single_traces", "false")
    _set_parameter(params, "report_convex_hulls", "true")
    _set_parameter(params, "report_chromatograms", "false")
    finder.setParameters(params)
    finder.run(traces, feature_map, chromatograms)
    return feature_map


def _rt_bounds(feature: oms.Feature) -> tuple[float, float]:
    hull = feature.getConvexHull()
    points = hull.getHullPoints()
    if len(points) > 0:
        box = hull.getBoundingBox()
        return float(box.minPosition()[0]), float(box.maxPosition()[0])
    half_width = max(float(feature.getWidth()) / 2.0, 0.5)
    return float(feature.getRT()) - half_width, float(feature.getRT()) + half_width


def _extract_trace(
    experiment: oms.MSExperiment,
    spectrum_rts: list[float],
    target_mz: float,
    start_rt: float,
    end_rt: float,
    config: PreprocessingConfig,
) -> tuple[np.ndarray, np.ndarray]:
    first = bisect_left(spectrum_rts, start_rt)
    last = bisect_right(spectrum_rts, end_rt)
    tolerance = max(
        config.xic_absolute_tolerance_da,
        target_mz * config.xic_tolerance_ppm / 1_000_000.0,
    )
    rts: list[float] = []
    intensities: list[float] = []
    for index in range(first, last):
        spectrum = experiment[index]
        mz_values, intensity_values = spectrum.get_peaks()
        left = int(np.searchsorted(mz_values, target_mz - tolerance, side="left"))
        right = int(np.searchsorted(mz_values, target_mz + tolerance, side="right"))
        intensity = float(np.max(intensity_values[left:right])) if right > left else 0.0
        rts.append(spectrum_rts[index])
        intensities.append(intensity)
    return np.asarray(rts, dtype=float), np.asarray(intensities, dtype=float)


def process_file(input_file: InputFile, config: PreprocessingConfig) -> tuple[list[IonFeature], dict[str, object]]:
    """Process one mzML file and return isotope-aware ion features plus QC."""
    experiment = _ensure_centroided(_load_ms1(input_file.path), input_file.path)
    spectrum_rts = [float(spectrum.getRT()) for spectrum in experiment]
    traces = _detect_mass_traces(experiment, config)
    feature_map = _find_isotope_features(traces, config)

    ordered = sorted(feature_map, key=lambda feature: (feature.getRT(), feature.getMZ()))
    features: list[IonFeature] = []
    prefix = "POS" if input_file.polarity == "POSITIVE" else "NEG"
    run_token = "".join(
        character if character.isalnum() else "_"
        for character in input_file.path.stem.upper()
    )
    for ordinal, feature in enumerate(ordered, start=1):
        if float(feature.getIntensity()) < config.min_feature_height:
            continue
        start_rt, end_rt = _rt_bounds(feature)
        trace_rts, trace_intensities = _extract_trace(
            experiment,
            spectrum_rts,
            float(feature.getMZ()),
            start_rt,
            end_rt,
            config,
        )
        nonzero_points = int(np.count_nonzero(trace_intensities > 0.0))
        if nonzero_points < config.min_trace_points:
            continue
        area = float(np.trapezoid(trace_intensities, trace_rts)) if len(trace_rts) > 1 else 0.0
        features.append(
            IonFeature(
                feature_id=f"{prefix}-{run_token}-F{ordinal:06d}",
                source_file=input_file.path.name,
                polarity=input_file.polarity,
                mz=float(feature.getMZ()),
                rt_seconds=float(feature.getRT()),
                intensity_height=float(feature.getIntensity()),
                intensity_area=area,
                charge=int(feature.getCharge()),
                quality=float(feature.getOverallQuality()),
                fwhm_seconds=float(feature.getWidth()),
                trace_start_seconds=start_rt,
                trace_end_seconds=end_rt,
                trace_point_count=nonzero_points,
                isotope_trace_count=len(feature.getConvexHulls()),
                trace_rts=trace_rts,
                trace_intensities=trace_intensities,
            )
        )

    qc = {
        "source_file": input_file.path.name,
        "polarity": input_file.polarity,
        "ms1_spectra": experiment.getNrSpectra(),
        "rt_start_seconds": min(spectrum_rts),
        "rt_end_seconds": max(spectrum_rts),
        "mass_traces_after_peak_splitting": len(traces),
        "openms_isotope_features": feature_map.size(),
        "exported_ion_features": len(features),
    }
    return features, qc


def process_inputs(
    inputs: Iterable[InputFile], config: PreprocessingConfig
) -> tuple[list[IonFeature], list[dict[str, object]]]:
    """Process inputs sequentially to keep pilot memory use bounded."""
    all_features: list[IonFeature] = []
    quality_control: list[dict[str, object]] = []
    for input_file in inputs:
        features, qc = process_file(input_file, config)
        all_features.extend(features)
        quality_control.append(qc)
    return all_features, quality_control

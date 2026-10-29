"""Typed records shared by preprocessing, grouping, and result writers."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import numpy as np


@dataclass(frozen=True)
class InputFile:
    """One mzML acquisition participating in the pilot."""

    path: Path
    polarity: str


@dataclass
class IonFeature:
    """One isotope-aware LC-MS ion feature detected by pyOpenMS."""

    feature_id: str
    source_file: str
    polarity: str
    mz: float
    rt_seconds: float
    intensity_height: float
    intensity_area: float
    charge: int
    quality: float
    fwhm_seconds: float
    trace_start_seconds: float
    trace_end_seconds: float
    trace_point_count: int
    isotope_trace_count: int
    trace_rts: np.ndarray = field(repr=False)
    trace_intensities: np.ndarray = field(repr=False)

    def result_record(self, group_id: str, group_size: int) -> dict[str, object]:
        """Return the public, serializable representation of this feature."""
        return {
            "group_id": group_id,
            "group_size": group_size,
            "is_correlated_group": group_size > 1,
            "feature_id": self.feature_id,
            "source_file": self.source_file,
            "polarity": self.polarity,
            "mz": self.mz,
            "rt_seconds": self.rt_seconds,
            "intensity_height": self.intensity_height,
            "intensity_area": self.intensity_area,
            "charge": self.charge,
            "quality": self.quality,
            "fwhm_seconds": self.fwhm_seconds,
            "trace_start_seconds": self.trace_start_seconds,
            "trace_end_seconds": self.trace_end_seconds,
            "trace_point_count": self.trace_point_count,
            "isotope_trace_count": self.isotope_trace_count,
        }


@dataclass(frozen=True)
class CorrelationEdge:
    """Accepted chromatographic similarity relation between two features."""

    left_index: int
    right_index: int
    correlation: float
    rt_overlap: float
    common_points: int


@dataclass
class FeatureGroup:
    """A deterministic chromatographic group, including singleton groups."""

    group_id: str
    features: list[IonFeature]


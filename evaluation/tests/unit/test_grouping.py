from __future__ import annotations

import numpy as np

from lipid_rule_eval.config import GroupingConfig
from lipid_rule_eval.grouping.correlation_graph import group_features, interval_overlap
from lipid_rule_eval.models import IonFeature


def feature(feature_id: str, mz: float, rt: float, values: list[float]) -> IonFeature:
    rts = np.arange(len(values), dtype=float)
    return IonFeature(
        feature_id=feature_id,
        source_file="pilot.mzML",
        polarity="POSITIVE",
        mz=mz,
        rt_seconds=rt,
        intensity_height=max(values),
        intensity_area=float(sum(values)),
        charge=1,
        quality=1.0,
        fwhm_seconds=2.0,
        trace_start_seconds=0.0,
        trace_end_seconds=float(len(values) - 1),
        trace_point_count=len(values),
        isotope_trace_count=1,
        trace_rts=rts,
        trace_intensities=np.asarray(values, dtype=float),
    )


def config() -> GroupingConfig:
    return GroupingConfig(
        rt_tolerance_seconds=1.2,
        minimum_rt_overlap=0.6,
        minimum_common_points=5,
        minimum_pearson_correlation=0.7,
    )


def test_correlated_features_are_grouped() -> None:
    left = feature("F1", 100.0, 10.0, [1, 2, 4, 8, 4, 2])
    right = feature("F2", 122.0, 10.5, [2, 4, 8, 16, 8, 4])

    groups, edges = group_features([left, right], config())

    assert len(groups) == 1
    assert len(groups[0].features) == 2
    assert len(edges) == 1


def test_distant_features_remain_singletons() -> None:
    left = feature("F1", 100.0, 10.0, [1, 2, 4, 8, 4, 2])
    right = feature("F2", 122.0, 20.0, [2, 4, 8, 16, 8, 4])

    groups, edges = group_features([left, right], config())

    assert len(groups) == 2
    assert not edges


def test_interval_overlap_is_intersection_over_union() -> None:
    left = feature("F1", 100.0, 10.0, [1, 2, 3, 2, 1])
    right = feature("F2", 122.0, 10.0, [1, 2, 3, 2, 1])
    right.trace_start_seconds = 1.0
    right.trace_end_seconds = 5.0

    assert interval_overlap(left, right) == 3.0 / 5.0

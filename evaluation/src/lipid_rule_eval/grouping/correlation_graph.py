"""Deterministic RT-overlap and Pearson-correlation feature grouping."""

from __future__ import annotations

import numpy as np

from lipid_rule_eval.config import GroupingConfig
from lipid_rule_eval.models import CorrelationEdge, FeatureGroup, IonFeature


def interval_overlap(left: IonFeature, right: IonFeature) -> float:
    """Return intersection-over-union for two chromatographic RT intervals."""
    intersection = max(
        0.0,
        min(left.trace_end_seconds, right.trace_end_seconds)
        - max(left.trace_start_seconds, right.trace_start_seconds),
    )
    union = max(left.trace_end_seconds, right.trace_end_seconds) - min(
        left.trace_start_seconds, right.trace_start_seconds
    )
    return intersection / union if union > 0.0 else 0.0


def trace_correlation(left: IonFeature, right: IonFeature) -> tuple[float, int]:
    """Correlate non-zero trace points matched to the nearest scan RT."""
    if left.trace_rts.size == 0 or right.trace_rts.size == 0:
        return float("nan"), 0
    rounded_left = {round(float(rt), 6): float(value) for rt, value in zip(left.trace_rts, left.trace_intensities)}
    rounded_right = {round(float(rt), 6): float(value) for rt, value in zip(right.trace_rts, right.trace_intensities)}
    common_rts = sorted(set(rounded_left).intersection(rounded_right))
    paired = [
        (rounded_left[rt], rounded_right[rt])
        for rt in common_rts
        if rounded_left[rt] > 0.0 and rounded_right[rt] > 0.0
    ]
    if len(paired) < 2:
        return float("nan"), len(paired)
    left_values = np.asarray([value[0] for value in paired], dtype=float)
    right_values = np.asarray([value[1] for value in paired], dtype=float)
    if np.std(left_values) == 0.0 or np.std(right_values) == 0.0:
        return float("nan"), len(paired)
    return float(np.corrcoef(left_values, right_values)[0, 1]), len(paired)


def candidate_edges(features: list[IonFeature], config: GroupingConfig) -> list[CorrelationEdge]:
    """Build accepted correlation edges using an RT-sorted sliding window."""
    ordered_indices = sorted(range(len(features)), key=lambda index: features[index].rt_seconds)
    edges: list[CorrelationEdge] = []
    for position, left_index in enumerate(ordered_indices):
        left = features[left_index]
        for right_index in ordered_indices[position + 1 :]:
            right = features[right_index]
            rt_delta = right.rt_seconds - left.rt_seconds
            if rt_delta > config.rt_tolerance_seconds:
                break
            if left.polarity != right.polarity or left.source_file != right.source_file:
                continue
            overlap = interval_overlap(left, right)
            if overlap < config.minimum_rt_overlap:
                continue
            correlation, common_points = trace_correlation(left, right)
            if common_points < config.minimum_common_points or not np.isfinite(correlation):
                continue
            if correlation >= config.minimum_pearson_correlation:
                edges.append(
                    CorrelationEdge(
                        left_index=left_index,
                        right_index=right_index,
                        correlation=correlation,
                        rt_overlap=overlap,
                        common_points=common_points,
                    )
                )
    return sorted(
        edges,
        key=lambda edge: (-edge.correlation, edge.left_index, edge.right_index),
    )


def group_features(features: list[IonFeature], config: GroupingConfig) -> tuple[list[FeatureGroup], list[CorrelationEdge]]:
    """Cluster accepted edges while bounding every group's apex RT span."""
    parents = list(range(len(features)))
    members = {index: {index} for index in range(len(features))}

    def find(index: int) -> int:
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    def union(left_index: int, right_index: int) -> None:
        left_root = find(left_index)
        right_root = find(right_index)
        if left_root == right_root:
            return
        combined = members[left_root] | members[right_root]
        apex_rts = [features[index].rt_seconds for index in combined]
        if max(apex_rts) - min(apex_rts) > config.rt_tolerance_seconds:
            return
        if left_root > right_root:
            left_root, right_root = right_root, left_root
        parents[right_root] = left_root
        members[left_root] = combined
        del members[right_root]

    edges = candidate_edges(features, config)
    for edge in edges:
        union(edge.left_index, edge.right_index)

    clusters: dict[int, list[IonFeature]] = {}
    for index, feature in enumerate(features):
        clusters.setdefault(find(index), []).append(feature)
    ordered_clusters = sorted(
        clusters.values(),
        key=lambda group: (
            group[0].polarity,
            min(feature.rt_seconds for feature in group),
            min(feature.mz for feature in group),
        ),
    )
    counters: dict[str, int] = {"POSITIVE": 0, "NEGATIVE": 0}
    groups: list[FeatureGroup] = []
    for cluster in ordered_clusters:
        polarity = cluster[0].polarity
        counters[polarity] += 1
        prefix = "POS" if polarity == "POSITIVE" else "NEG"
        groups.append(
            FeatureGroup(
                group_id=f"{prefix}-G{counters[polarity]:06d}",
                features=sorted(cluster, key=lambda feature: (feature.mz, feature.rt_seconds)),
            )
        )
    return groups, edges

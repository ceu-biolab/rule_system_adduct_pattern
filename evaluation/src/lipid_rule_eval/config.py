"""JSON configuration loading and validation."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from lipid_rule_eval.models import InputFile


@dataclass(frozen=True)
class PreprocessingConfig:
    mass_error_ppm: float
    noise_threshold_int: float
    chrom_peak_snr: float
    min_trace_points: int
    min_trace_length_seconds: float
    min_feature_height: float
    charge_lower_bound: int
    charge_upper_bound: int
    isotope_filtering_model: str
    xic_absolute_tolerance_da: float
    xic_tolerance_ppm: float


@dataclass(frozen=True)
class GroupingConfig:
    rt_tolerance_seconds: float
    minimum_rt_overlap: float
    minimum_common_points: int
    minimum_pearson_correlation: float


@dataclass(frozen=True)
class EvaluationConfig:
    dataset_id: str
    inputs: tuple[InputFile, ...]
    output_dir: Path
    preprocessing: PreprocessingConfig
    grouping: GroupingConfig
    future_annotation_context: dict[str, object]


@dataclass(frozen=True)
class AnnotationApiConfig:
    """Connection and mass-matching settings for the Java annotation API."""

    base_url: str
    tolerance_mode: str
    tolerance: float
    timeout_seconds: float


@dataclass(frozen=True)
class CompoundDatabaseConfig:
    """Read-only PostgreSQL compound lookup settings."""

    conninfo: str
    schema: str
    table: str
    mass_tolerance_ppm: float
    connect_timeout_seconds: int


@dataclass(frozen=True)
class AnnotationConfig:
    """Configuration for API annotation followed by neutral-mass lookup."""

    dataset_id: str
    input_json: Path
    output_dir: Path
    api: AnnotationApiConfig
    database: CompoundDatabaseConfig


@dataclass(frozen=True)
class RuleApiConfig:
    """Connection and retry settings for the Java rule endpoint."""

    base_url: str
    timeout_seconds: float
    max_retries: int


@dataclass(frozen=True)
class RuleExecutionConfig:
    """Configuration for class-specific expert-system scoring."""

    dataset_id: str
    input_json: Path
    output_dir: Path
    rule_resources_dir: Path
    api: RuleApiConfig
    mobile_phases: tuple[str, ...]
    sample_type: str
    tolerance_mode: str
    tolerance: float


def load_config(config_path: Path) -> EvaluationConfig:
    """Load a pilot configuration, resolving paths relative to the JSON file."""
    config_path = config_path.resolve()
    raw = json.loads(config_path.read_text(encoding="utf-8"))
    base = config_path.parent
    inputs = tuple(
        InputFile(path=(base / item["path"]).resolve(), polarity=item["polarity"].upper())
        for item in raw["inputs"]
    )
    if not inputs:
        raise ValueError("At least one mzML input is required")
    for item in inputs:
        if item.polarity not in {"POSITIVE", "NEGATIVE"}:
            raise ValueError(f"Unsupported polarity {item.polarity!r}")
        if not item.path.is_file():
            raise FileNotFoundError(item.path)

    preprocessing = PreprocessingConfig(**raw["preprocessing"])
    grouping = GroupingConfig(**raw["grouping"])
    if preprocessing.min_trace_points < 2:
        raise ValueError("min_trace_points must be at least 2")
    if not 0.0 <= grouping.minimum_rt_overlap <= 1.0:
        raise ValueError("minimum_rt_overlap must be between 0 and 1")
    if not -1.0 <= grouping.minimum_pearson_correlation <= 1.0:
        raise ValueError("minimum_pearson_correlation must be between -1 and 1")

    return EvaluationConfig(
        dataset_id=raw["dataset_id"],
        inputs=inputs,
        output_dir=(base / raw["output_dir"]).resolve(),
        preprocessing=preprocessing,
        grouping=grouping,
        future_annotation_context=raw.get("future_annotation_context", {}),
    )


def load_annotation_config(config_path: Path) -> AnnotationConfig:
    """Load annotation-stage settings and resolve its artifact paths."""
    config_path = config_path.resolve()
    raw = json.loads(config_path.read_text(encoding="utf-8"))
    base = config_path.parent
    section = raw.get("annotation")
    if not isinstance(section, dict):
        raise ValueError("Configuration has no 'annotation' section")

    input_json = (base / section["input_json"]).resolve()
    if not input_json.is_file():
        raise FileNotFoundError(input_json)
    api = AnnotationApiConfig(**section["api"])
    database = CompoundDatabaseConfig(**section["database"])
    if api.tolerance_mode not in {"PPM", "DALTON"}:
        raise ValueError("annotation.api.tolerance_mode must be PPM or DALTON")
    if api.tolerance <= 0 or api.timeout_seconds <= 0:
        raise ValueError("API tolerance and timeout must be positive")
    if database.mass_tolerance_ppm <= 0 or database.connect_timeout_seconds <= 0:
        raise ValueError("Database mass tolerance and timeout must be positive")
    if not database.schema or not database.table:
        raise ValueError("Database schema and table must be non-empty")

    return AnnotationConfig(
        dataset_id=raw["dataset_id"],
        input_json=input_json,
        output_dir=(base / section["output_dir"]).resolve(),
        api=api,
        database=database,
    )


def load_rule_execution_config(config_path: Path) -> RuleExecutionConfig:
    """Load rule-execution settings and validate fixed API vocabulary."""
    config_path = config_path.resolve()
    raw = json.loads(config_path.read_text(encoding="utf-8"))
    base = config_path.parent
    section = raw.get("rule_execution")
    if not isinstance(section, dict):
        raise ValueError("Configuration has no 'rule_execution' section")

    input_json = (base / section["input_json"]).resolve()
    resources = (base / section["rule_resources_dir"]).resolve()
    if not input_json.is_file():
        raise FileNotFoundError(input_json)
    if not resources.is_dir():
        raise FileNotFoundError(resources)

    api = RuleApiConfig(**section["api"])
    mobile_phases = tuple(section["mobile_phases"])
    valid_phases = {"NH4", "CH3CN", "CH3OH", "CH3COO", "HCOO"}
    if not mobile_phases or set(mobile_phases) - valid_phases:
        raise ValueError("rule_execution.mobile_phases contains unsupported values")
    if section["sample_type"] != "PLASMA":
        raise ValueError("PLASMA is currently the only supported sample type")
    if section["tolerance_mode"] not in {"PPM", "DALTON"}:
        raise ValueError("Rule tolerance mode must be PPM or DALTON")
    if section["tolerance"] <= 0 or api.timeout_seconds <= 0:
        raise ValueError("Rule tolerance and API timeout must be positive")
    if api.max_retries < 0:
        raise ValueError("API max_retries cannot be negative")

    return RuleExecutionConfig(
        dataset_id=raw["dataset_id"],
        input_json=input_json,
        output_dir=(base / section["output_dir"]).resolve(),
        rule_resources_dir=resources,
        api=api,
        mobile_phases=mobile_phases,
        sample_type=section["sample_type"],
        tolerance_mode=section["tolerance_mode"],
        tolerance=section["tolerance"],
    )

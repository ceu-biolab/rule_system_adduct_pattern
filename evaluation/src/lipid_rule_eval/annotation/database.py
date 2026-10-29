"""Read-only mass lookup against the local CMM compounds table."""

from __future__ import annotations

from typing import Any

import psycopg
from psycopg import sql
from psycopg.rows import dict_row

from lipid_rule_eval.annotation.lipid_type import enrich_compound_match
from lipid_rule_eval.config import CompoundDatabaseConfig


class CompoundRepository:
    """Read compound candidates whose monoisotopic mass is within a ppm window."""

    def __init__(self, config: CompoundDatabaseConfig):
        self.config = config
        self.connection: psycopg.Connection[Any] | None = None

    def __enter__(self) -> "CompoundRepository":
        conninfo = (
            f"{self.config.conninfo} "
            f"connect_timeout={self.config.connect_timeout_seconds}"
        )
        self.connection = psycopg.connect(conninfo, row_factory=dict_row)
        self.connection.execute("SET TRANSACTION READ ONLY")
        self.connection.execute("SET LOCAL statement_timeout = '60s'")
        return self

    def __exit__(self, exc_type: object, exc: object, traceback: object) -> None:
        if self.connection is not None:
            self.connection.rollback()
            self.connection.close()

    def connection_metadata(self) -> dict[str, str]:
        """Return non-secret database identity fields for provenance."""
        connection = self._connection()
        row = connection.execute(
            "SELECT current_database() AS database_name, current_user AS database_user"
        ).fetchone()
        return dict(row)

    def find_by_neutral_mass(self, neutral_mass: float) -> list[dict[str, Any]]:
        """Return every row within the configured mass-only tolerance."""
        if neutral_mass <= 0:
            return []
        tolerance_da = neutral_mass * self.config.mass_tolerance_ppm / 1_000_000.0
        query = sql.SQL(
            """
            SELECT c.compound_id, c.cas_id, c.compound_name, c.formula, c.mass,
                   c.charge_type, c.charge_number, c.formula_type, c.compound_type,
                   c.compound_status, c.formula_type_int, c.logp, c.rt_pred,
                   l.lipid_type AS cmm_lipid_type,
                   l.lipid_type_rt AS cmm_lipid_type_rt,
                   l.lipid_type_fragmentation AS cmm_lipid_type_fragmentation,
                   l.num_chains AS cmm_num_chains,
                   l.number_carbons AS cmm_number_carbons,
                   l.double_bonds AS cmm_double_bonds,
                   mass - %s AS mass_error_da,
                   (mass - %s) / %s * 1000000.0 AS mass_error_ppm
              FROM {}.{} c
              LEFT JOIN {}.compounds_lipids_classification l
                ON l.compound_id = c.compound_id
             WHERE c.mass > 0
               AND c.mass BETWEEN %s AND %s
             ORDER BY abs(c.mass - %s), c.compound_id
            """
        ).format(
            sql.Identifier(self.config.schema),
            sql.Identifier(self.config.table),
            sql.Identifier(self.config.schema),
        )
        rows = self._connection().execute(
            query,
            (
                neutral_mass,
                neutral_mass,
                neutral_mass,
                neutral_mass - tolerance_da,
                neutral_mass + tolerance_da,
                neutral_mass,
            ),
        ).fetchall()
        return [enrich_compound_match(dict(row)) for row in rows]

    def _connection(self) -> psycopg.Connection[Any]:
        if self.connection is None:
            raise RuntimeError("CompoundRepository must be used as a context manager")
        return self.connection

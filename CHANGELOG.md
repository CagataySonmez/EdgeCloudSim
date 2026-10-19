# Changelog

All notable changes to EdgeCloudSim are documented in this file. The project
does not currently publish a stable semantic-versioned release number, so the
changes below remain under `Unreleased` until maintainers assign one.

## Unreleased

### Added

- Deterministic experiment seeding through `-Dedgecloudsim.seed=<long>`.
- Repository-wide compilation, regression, configuration, dependency-integrity,
  and Javadoc checks in `scripts/quality_check.sh`.
- Linux/Java 21 continuous integration, citation metadata, a security policy,
  and SHA-256 checksums for vendored dependencies.
- Correctly spelled API aliases for historical typo-bearing names.

### Fixed

- VM selection, game-theory exclusion, multi-armed-bandit averaging, predictive
  statistics, queue stability, and zero-denominator reporting defects.
- Reinitialization of singleton configuration counters and handling of sparse
  WLAN identifiers.
- XML external-entity exposure and several path, encoding, resource-management,
  documentation, and shell-script portability defects.

### Compatibility Notes

- No existing public or protected JVM method or field descriptor has been
  removed or changed. Historical typo-bearing API names remain available.
- `TASK_STATUS.COMPLETED` is an identity alias for the historical
  `TASK_STATUS.COMLETED`; enum names, ordinals, serialization, and existing
  identity comparisons remain unchanged.
- Corrected stochastic sampling and scheduling algorithms can produce different
  simulation results from older revisions. Reproducing an older publication
  therefore requires the exact historical EdgeCloudSim revision, not only the
  same configuration and seed.
- Invalid numeric configuration, malformed XML, external DTD/entity use, and
  impossible runtime states now fail explicitly. Reusable library components
  throw exceptions instead of terminating the hosting JVM.
- Per-application `compile.sh` entry points now invoke the repository-wide
  compiler, so an error in any bundled application fails every compile entry
  point. The bundled CloudSim binary requires Java 21.


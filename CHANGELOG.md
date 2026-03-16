# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Add `scripts/release.py` for tag-driven Maven Central releases.
- Add artifact provenance metadata via JAR manifest `Build-Commit` / `Build-Tag` and POM `scm.tag`.
- Add `EnumMethodParametersTest`, an ASM-based bytecode regression test for enum constructor parameter metadata.

### Fixed

- Compile with `-parameters` so enum bytecode remains compatible with older D8/R8 versions such as AGP 7.4 / R8 4.0.52.

## [0.1.0] - 2026-03-04

### Added

- Lightweight Android tombstone protobuf implementation (core tombstone model, wire reader, and decoder).
- Initial Maven Central release.

[Unreleased]: https://github.com/abovevacant/epitaph/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/abovevacant/epitaph/tree/v0.1.0

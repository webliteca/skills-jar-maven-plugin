# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.2] - 2026-04-10

### Fixed

- Skill directory auto-detection now scans `skills/` for a single subdirectory containing `SKILL.md` when `skills/${artifactId}/` doesn't exist. Previously, the fallback returned the `skills/` directory itself even when the skill subdirectory name didn't match the Maven artifactId.

## [0.1.0] - 2026-04-10

### Added

- **`package` goal** — packages a skill folder into a `*-skills.jar` and attaches it as a secondary Maven artifact with classifier `skills`. Supports auto-detection of skill directories at `skills/${artifactId}/` or `skills/`.
- **`install` goal** — walks the project dependency tree, resolves `-skills.jar` artifacts, and unpacks them into `.claude/skills/` (configurable). Manages a `.skill-manifest.json` for incremental updates.
- **`list` goal** — reports which dependencies have skill bundles available, in human-readable or JSON format.
- **`verify` goal** — validates a skill folder against the Agent Skills format (SKILL.md frontmatter, structure, references).
- YAML frontmatter validation (required: `name`, `description`; name format enforcement).
- Zip-slip protection and size limit enforcement during JAR extraction.
- Configurable classifier, install directory, scope filtering, include/exclude patterns.
- `skill-library.properties` metadata in packaged JARs for traceability.
- Integration tests for single-module, multi-module, and invalid-skill scenarios.
- Unit tests for all validators, parsers, and utility classes.

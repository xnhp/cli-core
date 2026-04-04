## Why

`issue` currently parses `issue.yaml` with ad hoc SnakeYAML logic and hand-written field checks. This makes constraints harder to evolve, duplicates validation behavior, and does not provide a reusable path for other tools that also depend on YAML config files.

## What Changes

- Add reusable YAML loading + YAML-schema validation utilities to `cli-core` so multiple CLI projects (including `issue` and `PDE`) share one validation implementation.
- Define and ship a YAML schema for `issue.yaml` with required keys (`id`, `branch`) and optional rich fields (for example `title`) under a strict, explicit key contract.
- Update `issue` to call the `cli-core` loader/validator before binding to Kotlin models, replacing direct SnakeYAML parsing and manual required-key checks.
- Standardize validation error reporting so users get actionable path-based diagnostics when metadata files are invalid.

## Capabilities

### New Capabilities
- `yaml-schema-validated-config-loading`: Load YAML documents, validate them against YAML-defined schemas, and decode validated data into Kotlin types with consistent error diagnostics.
- `issue-yaml-schema-contract`: Define and enforce a schema-backed contract for `issue.yaml` so issue context resolution and property reads fail fast on invalid metadata.

### Modified Capabilities
- None.

## Impact

- Affected code: `cli-core` (new config/schema support package) and `issue` metadata loading code paths.
- Dependencies: add YAML parser + JSON Schema validator dependencies in `cli-core`; remove direct `snakeyaml` usage from `issue` parsing paths.
- Behavior: invalid `issue.yaml` files produce schema-based errors; valid files continue to work, and new fields are introduced by explicit schema updates.

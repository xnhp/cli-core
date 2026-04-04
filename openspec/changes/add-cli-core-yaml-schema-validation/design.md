## Context

`~/repos/issue` currently reads `issue.yaml` with direct SnakeYAML parsing and manual key checks embedded in command logic. Similar patterns exist in `~/repos/PDE`.

This duplicates validation behavior and creates inconsistent diagnostics when metadata is malformed. Multiple internal tools have the same need (YAML config + typed Kotlin models + strict contract validation), so adding reusable support in `cli-core` and consuming it from `issue`/`PDE` avoids copy/paste parser logic across projects.

## Goals / Non-Goals

**Goals:**
- Provide a reusable `cli-core` API to load YAML, validate against YAML-defined schemas, and decode into Kotlin data classes.
- Keep schema authoring in YAML while using JSON Schema semantics for expressiveness and existing validator maturity.
- Enforce `issue.yaml` contract in `issue` via `cli-core` before metadata is used for context resolution or property reads.
- Emit actionable validation diagnostics with instance paths and human-readable messages.

**Non-Goals:**
- Building a custom schema language independent of JSON Schema semantics.
- Adding automatic migration tooling for legacy malformed `issue.yaml` files.
- Reworking unrelated `issue` command behaviors beyond metadata parsing and validation.

## Decisions

1. Add a dedicated config package to `cli-core`
- Introduce `cn.varsa.cli.core.config` with focused APIs for parse/validate/decode operations and shared error formatting.
- Rationale: keeps schema/config concerns separated from command wiring while making reuse straightforward for other CLIs.
- Alternative considered: keep parser helpers in each app. Rejected due to duplication and inconsistent behavior.

2. Use YAML documents for both config and schema files
- Parse both schema and instance documents via Jackson YAML, then validate as JSON trees.
- Rationale: preserves YAML-first authoring while still enabling full JSON Schema constraints.
- Alternative considered: JSON schema files only. Rejected due to reduced readability for maintainers who prefer YAML.

3. Validate in `cli-core` before binding to Kotlin models
- `cli-core` will provide load-and-validate APIs, and `issue`/`PDE` will call those APIs so documents are validated before decoding to typed models such as `IssueMetadata`.
- Rationale: prevents partially decoded invalid models and centralizes error handling.
- Alternative considered: decode first, then validate via Kotlin validators. Rejected because it loses schema-tool interoperability and broad structural checks.

4. Ship `issue` schema as a versioned resource with strict unknown-key policy
- Define required keys (`id`, `branch`) and optional `title`; reject unknown keys unless they are added through an explicit schema update.
- Rationale: keeps the contract explicit and ensures typos or undocumented fields fail fast.
- Alternative considered: extension-key patterns (such as `x-*`) for ad hoc metadata. Rejected to keep schema evolution intentional and centrally reviewed.

5. Standardize diagnostics contract
- `cli-core` exposes validation issues including instance path, schema path, and message; callers raise `CliException` with compact multi-line output.
- Rationale: keeps consistent CLI UX and reduces bespoke formatting logic.
- Alternative considered: raw validator error output. Rejected as noisy and tool-specific.

## Risks / Trade-offs

- [Dependency footprint growth in `cli-core`] -> Mitigation: keep new dependencies scoped to config package and avoid optional feature sprawl.
- [Validator differences from existing ad hoc behavior] -> Mitigation: add compatibility tests for current valid `issue.yaml` shapes and explicit tests for newly rejected cases.
- [Schema evolution may break existing custom fields] -> Mitigation: require explicit schema updates for new fields and document migration expectations in changelog/release notes.
- [Potential performance overhead when repeatedly loading schema] -> Mitigation: add simple in-memory schema cache keyed by absolute path/resource identifier.

## Migration Plan

1. Implement `cli-core` YAML schema validation utilities and tests, including tests that read YAML files and validate them against schema files.
2. Add `issue.yaml` schema file in `issue` and integrate loader usage in metadata read/parse paths.
3. Replace direct SnakeYAML-based parsing helpers in `issue` with validated decode calls.
4. Update tests to assert schema-based failures and path-rich error messages.
5. Remove now-unused `snakeyaml` dependency from `issue` if no other direct usage remains.
6. Rollback plan: restore previous parsing functions and dependency wiring if validation integration blocks critical workflows.

## Open Questions

- None.

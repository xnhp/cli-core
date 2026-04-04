## Context

`cli-core` currently models command name, description, aliases, and execution handlers, but has no first-class place to store maturity metadata. Consumers like `pde` previously surfaced maturity in help text by hand and lost that output after moving to shared command-tree wiring.

The change should restore maturity visibility in a reusable way, avoid per-command string hacks, and keep generated help/documentation deterministic.

## Goals / Non-Goals

**Goals:**
- Add optional maturity metadata to `CliCommandGroup` and `CliCommandLeaf`.
- Render maturity consistently in help output (both command usage description and subcommand listings).
- Keep existing command definitions source-compatible when maturity is not set.

**Non-Goals:**
- Implement maturity-specific runtime policy (blocking execution, warnings, telemetry).
- Auto-migrate every downstream app in this change.
- Introduce localization for maturity labels.

## Decisions

- Add a typed maturity field at the CLI model layer (instead of embedding maturity text in descriptions).
  - Rationale: avoids duplication and keeps semantic metadata separate from prose.
  - Alternative considered: keep manual description suffixes in each app. Rejected because drift/regression already occurred.

- Keep maturity optional and additive.
  - Rationale: no forced migration for existing command trees and no behavior changes unless a command opts in.

- Render maturity as plain, stable text in help descriptions (for example `[usable]`, `[WIP]`) using shared formatting.
  - Rationale: help output is consumed by docs generation, so formatting must be deterministic and free of terminal-specific control behavior.
  - Alternative considered: ANSI-colored maturity in help text. Rejected for generated-doc stability and portability.

- Centralize help-description decoration in `CliCore` command-line construction.
  - Rationale: one place controls representation for both groups and leaves and prevents downstream inconsistencies.

## Risks / Trade-offs

- [Formatting drift in snapshots/docs] -> Add help output tests covering root command listings and leaf usage text.
- [Ambiguity in maturity vocabulary] -> Start with a small supported set and explicit labels; document extension expectations.
- [Consumer expectations on old text] -> Keep description text unchanged when maturity is unset.

## Migration Plan

1. Add optional maturity metadata in `cli-core` command model and help rendering.
2. Add/adjust `cli-core` tests for decorated help output.
3. Consumer apps adopt maturity metadata progressively (starting with `pde`) without required compatibility shims.

Rollback: remove/deactivate maturity decoration while keeping command descriptions intact.

## Open Questions

- Should maturity be a strict enum only, or enum + custom label escape hatch?
- Should deprecated maturity also emit runtime warnings at command execution time in a follow-up change?

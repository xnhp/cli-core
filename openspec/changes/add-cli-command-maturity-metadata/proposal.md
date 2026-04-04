## Why

`pde` command help previously exposed maturity labels (for example `usable`, `WIP`) and that signal was lost when command wiring moved to `cli-core` command trees. Maturity is useful to set user expectations and should be modeled once in the shared CLI layer instead of reimplemented per app.

## What Changes

- Add first-class command maturity metadata to `cli-core` command definitions (`CliCommandGroup` and `CliCommandLeaf`).
- Render maturity in generated help output for commands and subcommands, with consistent formatting.
- Keep maturity optional so existing command definitions remain valid without migration.
- Document how applications can annotate commands with maturity and how this appears in help output.

## Capabilities

### New Capabilities
- `cli-command-maturity-metadata`: Let command definitions declare maturity level and surface that metadata in help output produced by `CliMain`.

### Modified Capabilities
- None.

## Impact

- Affected code: `src/main/kotlin/cn/varsa/cli/core/CliCore.kt` and related `cli-core` tests.
- Affected consumers: CLI apps built on `cli-core` (for example `pde`) can opt into maturity labels by setting metadata in command definitions.
- Dependencies/APIs: no new external dependency; additive API changes in command model types.

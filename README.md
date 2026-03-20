# cli-core

`cli-core` is a small Kotlin library for bootstrapping Picocli-based command-line applications.
It provides reusable building blocks for logging, color output, command discovery, process execution, and consistent CLI error handling.

## Who this is for

This README is written for AI agents (and humans) that need a fast orientation before editing or extending this project.

## Quick facts

- **Language/tooling**: Kotlin JVM (`kotlin("jvm")`), Gradle Kotlin DSL.
- **Java target**: JVM toolchain 17.
- **CLI framework**: Picocli (`info.picocli:picocli:4.7.6`).
- **Artifact**: Maven publication `cn.varsa:cli-core`.
- **Entry code location**: `src/main/kotlin/cn/varsa/cli/core/CliCore.kt`.

## What this library provides

All core utilities currently live in one file: `src/main/kotlin/cn/varsa/cli/core/CliCore.kt`.

- `ColorMode`: normalizes color behavior (`AUTO`, `ALWAYS`, `NEVER`) from user input.
- `CliLogLevel`: maps CLI-friendly log levels to `java.util.logging.Level` and resolves level from flags.
- `CliFailure` / `CliException`: typed runtime failures with an exit code.
- `CliStyle`: ANSI label/color helpers (`INFO`, `WARN`, `ERROR`, success/warn/danger text styles).
- `CliLogging`: configures JUL root logger handlers with formatted, color-aware output.
- `CliProcess`: helpers to run subprocesses with either captured or streaming output.
- `CliMain`: wrapper around Picocli execution with centralized exception-to-exit-code handling.
- `CliCommands`: recursively discovers command trees and builds a full command path string.

## Build and run basics

From repo root:

```bash
./gradlew build
```

Output JAR:

- `build/libs/cli-core-<version>.jar`

## AI agent working notes

When making changes, prefer these conventions:

1. Keep behavior library-like and reusable (avoid app-specific assumptions).
2. Preserve exit-code-driven failure paths (`CliFailure`) instead of raw exceptions.
3. Keep logging output deterministic and terse; color should remain optional.
4. If adding new utilities, keep API surface small and cohesive with existing `Cli*` objects.
5. Update this README when introducing new public helpers or moving files.

## Suggested extension points

- Split `CliCore.kt` into focused files as the library grows (`logging`, `process`, `commands`, `style`).
- Add tests for parsing/resolution logic in `ColorMode` and `CliLogLevel`.
- Add tests around `CliMain` exception handler behavior and `CliProcess` failure formatting.

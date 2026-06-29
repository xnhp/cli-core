# cli-core

`cli-core` is a small Kotlin library for bootstrapping Picocli-based command-line applications.
It provides reusable building blocks for logging, color output, command discovery, process execution, and consistent CLI error handling.

## Who this is for

This README is written for AI agents (and humans) that need a fast orientation before editing or extending this project.

## Quick facts

- **Language/tooling**: Kotlin JVM (`kotlin("jvm")`), Gradle Kotlin DSL.
- **Java target**: JVM toolchain 21.
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
- `CliCommandGroup` / `CliCommandLeaf`: declarative command definitions that can be executed through Picocli.
- `CliToolBinding`: opt-in MCP metadata for exposing declarative command leaves as tools.

## MCP support

MCP support is currently part of the main `cn.varsa:cli-core` artifact. This intentionally brings in the Kotlin MCP SDK and its runtime dependencies for consumers of this artifact; split packaging can be revisited if CLI-only consumers need a lighter dependency graph.

MCP tool registration is scoped to cli-core's declarative command API. Only `CliCommandLeaf` instances with an explicit `tool = CliToolBinding(...)` are registered; arbitrary Picocli `CommandLine` or `@Command` graphs are not discovered as MCP tools.

Basic usage:

```kotlin
val root = CliCommandGroup(
  name = "app",
  description = "Example app",
  children = listOf(
    CliCommandLeaf(
      name = "hello",
      description = "Print a greeting",
      options = listOf(CliOption(listOf("--name"), "Name to greet", takesValue = true, required = true)),
      tool = CliToolBinding(id = "hello"),
      handler = { args ->
        println("hello ${'$'}{args.last()}")
        0
      }
    )
  )
)

val server = createCliMcpServer(root)
```

Use `runCliMcpStdioServer(root)` for local agent/editor integrations that spawn the application as an MCP subprocess over stdin/stdout. Use `createCliMcpServer(root)` when an application wants a preconfigured MCP `Server` with cli-core tools registered. Use `server.registerCliTools(root)` when the application already owns the MCP server lifecycle or transport setup.

By default, MCP input schemas are generated from `CliOption` and `CliPositionalArg` metadata. Set `CliToolBinding(inputSchema = ...)` and `decodeArguments = ...` for commands whose tool input does not map cleanly to the generated schema.

MCP command execution is serialized inside cli-core because command handlers may still write to global stdout/stderr or depend on process-wide state. `user.dir` is not changed by default; applications can opt into per-call cwd changes with `CliMcpRegistrationConfig(workingDirectoryProvider = ...)`, but this is also serialized.

## Build and run basics

From repo root:

```bash
./gradlew build
```

Output JAR:

- `build/libs/cli-core-<version>.jar`

## Development

Use Java 21 for local development. The Gradle wrapper is checked in so CI and
local builds use the same Gradle distribution:

```bash
./gradlew build
./gradlew test
```

Consumers should keep their dependency on the Maven coordinate:

```kotlin
implementation("cn.varsa:cli-core:0.1.0-SNAPSHOT")
```

For source-level local development, consumer repositories use Gradle composite
build substitution. Pass an explicit checkout path when the default sibling
layout is not available:

```bash
./gradlew test -PcliCorePath=/home/ben/repos/cli-core
```

Do not switch consumers to `project(...)` dependencies; included builds
substitute the published coordinate and keep local development aligned with CI.

## Publishing

`cli-core` publishes to GitHub Packages as `cn.varsa:cli-core` in the
`xnhp/cli-core` package repository:

```bash
./gradlew publish
```

Publishing credentials are read from Gradle properties first and then from the
GitHub Actions environment:

```properties
gpr.user=<github-user>
gpr.key=<token-with-write-packages>
```

The same publication can be deployed by the `Publish` GitHub Actions workflow.
It runs on tags matching `cli-core-v*` and on manual `workflow_dispatch` runs.

Consumers resolving the private package need `read:packages` access. In GitHub
Actions this normally means `permissions: packages: read` plus an explicit
`GITHUB_TOKEN` environment variable for the Gradle process. Cross-repository
access also requires the package to grant the consuming repository access, or a
PAT with `read:packages` configured as a secret.

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

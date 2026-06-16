# PDE MCP Tool Layout Draft

Goal: expose high-level PDE development workflows as ergonomic MCP tools. The tools should be stable workflow operations, not a 1:1 mirror of every CLI command. CLI commands remain useful transport/execution primitives, but MCP tools should use structured inputs and domain-specific names where that improves agent usability.

## Design Direction

1. Prefer task-oriented tools such as `pde_compile_workspace` and `pde_run_test` over command-path tools such as `pde.compile` or `pde.test`.
2. Use structured inputs for common choices: config path, launch name, test selectors, workspace roots, target roots, log level, output paths, and dry-run flags.
3. Keep read/inspect tools separate from mutating tools so clients can choose safe operations first.
4. Do not expose server/bootstrap commands such as `pde mcp` as MCP tools.
5. Decouple MCP tools from CLI commands where one workflow needs orchestration, defaults, validation, or cleaner output than the CLI provides.

## Primary Tools

### `pde_compile_workspace`

Compile PDE Java bundles for a workspace or launch configuration.

Inputs:

- `config`: optional path to `pde.yaml` or equivalent launch config.
- `workspace`: optional array of workspace bundle directories.
- `fullRebuild`: boolean, default false.

Backs onto CLI capability: `pde compile`.

Notes:

- This should be a first-class MCP tool, not just raw args passthrough.
- Return structured compile summary: exit code, compiled bundle count, skipped count, failed bundles, results JSON path when available.

### `pde_run_launch`

Run a configured PDE launch.

Inputs:

- `config`: optional path to launch config.
- `launch`: optional launch name.

Backs onto CLI capability: `pde run` / `pde launch`.

Notes:

- Expose one MCP tool only. Do not expose both `run` and `launch` aliases.
- Prefer `pde_run_launch` as the public MCP name because it describes the workflow.

### `pde_run_test`

Run one or more PDE test launches.

Inputs:

- `config`: optional path to launch config.
- `tests`: optional array of test names or indexes; empty means all configured tests.

Backs onto CLI capability: `pde test`.

Notes:

- This is a high-value agent tool: “run this test” should not require constructing CLI args.
- Return structured summary when possible: total tests, failures, report paths, timeout status.

### `pde_prepare_target`

Resolve or prepare target platform state for a config.

Inputs:

- `config`: optional path to launch config.

Backs onto CLI capability: `pde target install`.

Notes:

- Use this instead of exposing the lower-level “install” command name directly.
- Mark as mutating because it writes target/cache/profile state.


## Tools To Avoid Exposing

1. `pde_mcp` / `pde.mcp`: starting an MCP server from an MCP server is not useful.
2. Duplicate alias tools for both `run` and `launch`: expose only `pde_run_launch`.
3. Raw command passthrough as a primary interface: keep only as an escape hatch if needed.

## Possible Escape Hatch

agent can just run CLI commands directly

## Decoupling Recommendation

PDE should decouple MCP tools from CLI leaves for the high-value workflows:

1. Keep CLI commands as execution backends.
2. Define MCP tools around workflows and structured domain inputs.
3. Use custom `CliToolBinding(inputSchema, decodeArguments)` or direct MCP executors where generated CLI schemas are too CLI-shaped.
4. Combine closely related CLI subcommands into single MCP tools when a selector is more ergonomic, especially target inspection and IDE initialization.

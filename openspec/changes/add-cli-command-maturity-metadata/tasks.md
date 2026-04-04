## 1. Command Model Metadata

- [ ] 1.1 Add optional maturity metadata to `CliCommandGroup` and `CliCommandLeaf` in `CliCore.kt`.
- [ ] 1.2 Add shared maturity label formatting helper(s) for deterministic plain-text help output.

## 2. Help Rendering Integration

- [ ] 2.1 Update command-line construction to decorate command descriptions with maturity labels when set.
- [ ] 2.2 Ensure the same maturity representation appears in both parent subcommand listings and command-specific help.

## 3. Verification

- [ ] 3.1 Add/update `cli-core` tests that assert help text for group and leaf commands with/without maturity metadata.
- [ ] 3.2 Verify help output contains no ANSI sequences in non-interactive capture and remains unchanged for commands without maturity.
- [ ] 3.3 Run `gradle test` and confirm all tests pass.

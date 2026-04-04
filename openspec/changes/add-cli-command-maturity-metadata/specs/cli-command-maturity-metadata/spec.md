## ADDED Requirements

### Requirement: Commands declare maturity metadata
The CLI command model SHALL support an optional maturity field for both command groups and command leaves.

#### Scenario: Command omits maturity
- **WHEN** a command definition does not set maturity metadata
- **THEN** CLI behavior and help output remain unchanged from current behavior

#### Scenario: Command sets maturity
- **WHEN** a command definition sets maturity metadata
- **THEN** the maturity value is retained in the command model and available to help rendering

### Requirement: Help output shows maturity consistently
When maturity metadata is present, generated help output MUST include maturity labels in command descriptions so users can see command stability in top-level and nested command help.

#### Scenario: Root command lists subcommands with maturity
- **WHEN** a parent command prints help and one or more subcommands have maturity metadata
- **THEN** each annotated subcommand entry includes its maturity label in the displayed description

#### Scenario: Command-specific help includes maturity
- **WHEN** help is printed for a specific command with maturity metadata
- **THEN** that command description includes the same maturity label format used in parent command listings

### Requirement: Maturity rendering is deterministic for documentation
Maturity labels in help output MUST render as stable plain text so generated documentation can consume help output without terminal-specific escape artifacts.

#### Scenario: Help output consumed for docs generation
- **WHEN** help output is captured in a non-interactive environment
- **THEN** maturity labels are rendered as plain textual labels without ANSI control sequences

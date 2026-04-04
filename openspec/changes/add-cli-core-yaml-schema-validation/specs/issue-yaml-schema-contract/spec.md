## ADDED Requirements

### Requirement: issue.yaml SHALL be schema-validated before use
The `issue` tool SHALL validate `issue.yaml` against a defined YAML schema before using metadata for issue context resolution or property reads.

#### Scenario: Validation is executed by shared cli-core logic
- **WHEN** `issue` or `PDE` needs to read schema-controlled YAML metadata
- **THEN** the tool delegates loading and schema validation to shared `cli-core` APIs instead of implementing per-tool parser/validator logic

#### Scenario: Valid metadata supports issue context resolution
- **WHEN** `issue.yaml` contains schema-valid metadata with required keys
- **THEN** `issue` resolves issue context and reads properties using the validated metadata

#### Scenario: Invalid metadata blocks context usage
- **WHEN** `issue.yaml` violates schema constraints
- **THEN** `issue` fails fast with a validation error and does not continue with metadata-dependent operations

### Requirement: issue.yaml schema SHALL define explicit required and optional keys
The `issue.yaml` schema SHALL require `id` and `branch`, support optional rich fields such as `title`, and reject unknown keys unless those keys are added through an explicit schema update.

#### Scenario: Optional title is present
- **WHEN** `issue.yaml` includes `title` with valid scalar content
- **THEN** validation succeeds and the title is available to metadata consumers

#### Scenario: Unknown key is rejected
- **WHEN** `issue.yaml` contains a key outside the defined contract
- **THEN** validation fails with an error that identifies the unknown key path

### Requirement: issue commands SHALL surface schema diagnostics consistently
The `issue` CLI SHALL present schema validation failures through a consistent diagnostic format suitable for terminal usage.

#### Scenario: Read command encounters invalid issue.yaml
- **WHEN** `issue read <prop>` discovers an invalid `issue.yaml`
- **THEN** the command exits with a clear validation summary that includes each failing path and message

## ADDED Requirements

### Requirement: YAML schema validation for typed config loading
The system SHALL provide a reusable API in `cli-core` that loads YAML documents, validates them against a YAML-defined schema using JSON Schema semantics, and decodes validated content into Kotlin types.

#### Scenario: Valid document decodes successfully
- **WHEN** a caller loads a YAML document that satisfies all schema constraints
- **THEN** the system returns a successfully decoded Kotlin model instance

#### Scenario: Invalid document is rejected before decode
- **WHEN** a caller loads a YAML document that violates schema constraints
- **THEN** the system fails validation and does not decode the document into a Kotlin model

### Requirement: cli-core SHALL verify file-based YAML/schema loading behavior with tests
The system SHALL include automated tests in `cli-core` that read YAML instance files and YAML schema files from disk/resources and validate expected pass/fail outcomes.

#### Scenario: Valid YAML file passes schema validation
- **WHEN** a test loads a schema YAML file and a compliant YAML instance file
- **THEN** validation succeeds and decoding to the target model is possible

#### Scenario: Invalid YAML file fails schema validation
- **WHEN** a test loads a schema YAML file and a non-compliant YAML instance file
- **THEN** validation fails with at least one deterministic validation issue

### Requirement: Validation diagnostics are actionable and stable
The system SHALL report validation failures with machine-locatable and user-actionable details, including instance path, schema path, and human-readable message.

#### Scenario: Required field is missing
- **WHEN** a YAML document omits a required property defined by schema
- **THEN** the reported error includes the missing property context and a path identifying where the violation occurred

#### Scenario: Type mismatch is detected
- **WHEN** a YAML property value has a type that does not match schema
- **THEN** the reported error identifies the property path and expected type constraint

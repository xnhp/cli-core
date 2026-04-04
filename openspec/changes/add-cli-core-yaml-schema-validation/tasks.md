## 1. Add reusable YAML schema validation in cli-core

- [x] 1.1 Add YAML/config dependencies in `cli-core` (`jackson-dataformat-yaml`, `jackson-module-kotlin`, JSON Schema validator) and keep versions aligned with existing build conventions.
- [x] 1.2 Implement a `cli-core` config package that loads YAML documents and schema files into JSON trees, validates documents against schema, and decodes validated trees into Kotlin models.
- [x] 1.3 Implement a stable validation issue model and error formatter that includes instance path, schema path, and human-readable message.
- [x] 1.4 Add unit tests in `cli-core` for valid decode, missing required key, type mismatch, unknown-key rejection, and file-based YAML+schema loading/validation behavior.

## 2. Define and wire issue.yaml schema contract

- [x] 2.1 Add `issue.yaml` schema file in `issue` resources with required `id` and `branch`, optional `title`, and strict unknown-key rejection.
- [x] 2.2 Refactor `issue` metadata loading/parsing paths to use the new validated loader API instead of direct SnakeYAML parsing.
- [x] 2.3 Update `issue` error handling so metadata validation failures are surfaced consistently through `CliException` diagnostics.
- [x] 2.4 Remove direct `snakeyaml` dependency/usage from `issue` if no longer needed after integration.

## 3. Verify behavior and compatibility

- [x] 3.1 Update `issue` tests to cover schema-valid files, missing required keys, invalid types, and unknown-key rejection.
- [x] 3.2 Verify commands that depend on `issue.yaml` (`init`, `new`, `read`, `pick` discovery paths) continue to work for valid metadata and fail fast for invalid metadata.
- [x] 3.3 Run project tests in both repositories and document any migration notes for teams with existing custom `issue.yaml` fields.

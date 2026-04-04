package cn.varsa.cli.core.config

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamlConfigTest {
  data class Metadata(
    val id: String,
    val branch: String,
    val title: String? = null
  )

  data class DefaultsExample(
    val id: String,
    val enabled: Boolean,
    val runner: String,
    val args: List<String>
  )

  private val resourcesDir = Path.of("src", "test", "resources", "config")
  private val schema = YamlConfig.loadSchema(resourcesDir.resolve("test.schema.yaml"))
  private val defaultsSchema = YamlConfig.loadSchema(resourcesDir.resolve("defaults.schema.yaml"))

  @Test
  fun `decode validated succeeds for valid yaml`() {
    val parsed = YamlConfig.decodeValidated(resourcesDir.resolve("valid.yaml"), schema, Metadata::class.java)

    assertEquals("NXT-123", parsed.id)
    assertEquals("issue/NXT-123", parsed.branch)
    assertEquals("Test title", parsed.title)
  }

  @Test
  fun `missing required key returns validation issue`() {
    val issues = YamlConfig.validate(resourcesDir.resolve("missing-id.yaml"), schema)

    assertFalse(issues.isEmpty())
    assertTrue(issues.any { it.message.contains("required", ignoreCase = true) })
  }

  @Test
  fun `type mismatch returns validation issue`() {
    val issues = YamlConfig.validate(resourcesDir.resolve("invalid-type.yaml"), schema)

    assertFalse(issues.isEmpty())
    assertTrue(issues.any { it.instancePath.contains("branch") || it.message.contains("string", ignoreCase = true) })
  }

  @Test
  fun `unknown key is rejected by additional properties`() {
    val issues = YamlConfig.validate(resourcesDir.resolve("unknown-key.yaml"), schema)

    assertFalse(issues.isEmpty())
    assertTrue(issues.any { it.message.contains("additional", ignoreCase = true) || it.message.contains("unknown") })
  }

  @Test
  fun `validate and parse map returns scalar values for valid yaml`() {
    val root = YamlConfig.validateAndParseMap(resourcesDir.resolve("valid.yaml"), schema)

    assertEquals("NXT-123", root["id"])
    assertEquals("issue/NXT-123", root["branch"])
  }

  @Test
  fun `decode validated applies schema defaults`() {
    val parsed = YamlConfig.decodeValidated(
      resourcesDir.resolve("defaults-input.yaml"),
      defaultsSchema,
      DefaultsExample::class.java
    )

    assertEquals("cfg-1", parsed.id)
    assertEquals(false, parsed.enabled)
    assertEquals("junit5", parsed.runner)
    assertEquals(emptyList(), parsed.args)
  }

  @Test
  fun `validate and parse map applies defaults for missing keys`() {
    val root = YamlConfig.validateAndParseMap(resourcesDir.resolve("defaults-input.yaml"), defaultsSchema)

    assertEquals("cfg-1", root["id"])
    assertEquals(false, root["enabled"])
    assertEquals("junit5", root["runner"])
    assertEquals(emptyList<String>(), root["args"])
  }

  @Test
  fun `format issues avoids duplicating instance path`() {
    val issues = listOf(
      YamlValidationIssue(
        instancePath = "$",
        schemaPath = "https://example.local/schemas/pde-config.schema.yaml#/additionalProperties",
        message = "$: property 'targetFile' is not defined in the schema and the schema does not allow additional properties"
      )
    )

    val formatted = YamlConfig.formatIssues(issues)

    assertEquals(
      "- $: property 'targetFile' is not defined in the schema and the schema does not allow additional properties",
      formatted
    )
  }

  @Test
  fun `format issues removes trailing schema uri from message`() {
    val issues = listOf(
      YamlValidationIssue(
        instancePath = "$.build",
        schemaPath = "https://example.local/schemas/pde-config.schema.yaml#/properties/build",
        message = "$.build: must be string [https://example.local/schemas/pde-config.schema.yaml#/properties/build/type]"
      )
    )

    val formatted = YamlConfig.formatIssues(issues)

    assertEquals("- $.build: must be string", formatted)
  }
}

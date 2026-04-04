package cn.varsa.cli.core.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

data class YamlValidationIssue(
  val instancePath: String,
  val schemaPath: String,
  val message: String
)

class YamlSchema internal constructor(internal val schemaNode: JsonNode)

object YamlConfig {
  private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
  private val jsonSchemaFactory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

  fun loadSchema(path: Path): YamlSchema = YamlSchema(parseYaml(path))

  fun loadSchema(inputStream: InputStream): YamlSchema = inputStream.use {
    YamlSchema(parseYaml(it))
  }

  fun parseMap(path: Path): Map<String, Any?> =
    parseMap(path.toFile().readText())

  fun parseMap(content: String): Map<String, Any?> {
    val root = parseYaml(content)
    return mapper.convertValue(root, object : TypeReference<Map<String, Any?>>() {})
  }

  fun validate(path: Path, schema: YamlSchema): List<YamlValidationIssue> =
    validate(path.toFile().readText(), schema)

  fun validate(content: String, schema: YamlSchema): List<YamlValidationIssue> {
    val document = parseYaml(content)
    return validate(document, schema)
  }

  fun validateAndParseMap(content: String, schema: YamlSchema): Map<String, Any?> {
    val document = parseYaml(content)
    val defaulted = applyDefaults(document, schema.schemaNode, schema.schemaNode)
    val issues = validate(defaulted, schema)
    if (issues.isNotEmpty()) {
      throw IllegalArgumentException(formatIssues(issues))
    }
    return mapper.convertValue(defaulted, object : TypeReference<Map<String, Any?>>() {})
  }

  fun <T : Any> decodeValidated(content: String, schema: YamlSchema, targetType: Class<T>): T {
    val document = parseYaml(content)
    val defaulted = applyDefaults(document, schema.schemaNode, schema.schemaNode)
    val issues = validate(defaulted, schema)
    if (issues.isNotEmpty()) {
      throw IllegalArgumentException(formatIssues(issues))
    }
    return mapper.treeToValue(defaulted, targetType)
  }

  private fun validate(document: JsonNode, schema: YamlSchema): List<YamlValidationIssue> {
    val compiledSchema = jsonSchemaFactory.getSchema(schema.schemaNode)
    val messages = compiledSchema.validate(document)
    return messages
      .map { message ->
        YamlValidationIssue(
          instancePath = normalizePath(message.instanceLocation.toString()),
          schemaPath = normalizePath(message.schemaLocation.toString()),
          message = message.message
        )
      }
      .sortedBy { it.instancePath + it.schemaPath + it.message }
  }

  fun validateAndParseMap(path: Path, schema: YamlSchema): Map<String, Any?> {
    return validateAndParseMap(path.toFile().readText(), schema)
  }

  fun <T : Any> decodeValidated(path: Path, schema: YamlSchema, targetType: Class<T>): T {
    return decodeValidated(path.toFile().readText(), schema, targetType)
  }

  fun formatIssues(issues: List<YamlValidationIssue>): String =
    issues.joinToString(separator = "\n") { issue ->
      val normalizedMessage = normalizeIssueMessage(issue)
      "- ${issue.instancePath}: ${normalizedMessage}"
    }

  private fun normalizeIssueMessage(issue: YamlValidationIssue): String {
    var message = issue.message.trim()
    val instancePrefix = "${issue.instancePath}:"
    if (message.startsWith(instancePrefix)) {
      message = message.removePrefix(instancePrefix).trimStart()
    }
    message = message.replace(Regex("\\s*\\[[a-zA-Z][a-zA-Z0-9+.-]*://[^\\]]+\\]$"), "")
    return message
  }

  private fun parseYaml(path: Path): JsonNode {
    Files.newInputStream(path).use { return parseYaml(it) }
  }

  private fun parseYaml(inputStream: InputStream): JsonNode {
    return mapper.readTree(inputStream) ?: throw IllegalArgumentException("YAML document is empty")
  }

  private fun parseYaml(content: String): JsonNode {
    return mapper.readTree(content) ?: throw IllegalArgumentException("YAML document is empty")
  }

  private fun applyDefaults(node: JsonNode, schemaNode: JsonNode, rootSchemaNode: JsonNode): JsonNode {
    val resolvedSchema = resolveSchema(schemaNode, rootSchemaNode)
    val defaultedNode = node.deepCopy<JsonNode>()

    when {
      defaultedNode is ObjectNode -> {
        val properties = resolvedSchema.get("properties") as? ObjectNode
        properties?.fields()?.forEach { (key, childSchemaNode) ->
          if (defaultedNode.has(key)) {
            val childNode = defaultedNode.get(key)
            defaultedNode.set<JsonNode>(key, applyDefaults(childNode, childSchemaNode, rootSchemaNode))
          } else {
            val childSchema = resolveSchema(childSchemaNode, rootSchemaNode)
            val defaultValue = childSchemaNode.get("default") ?: childSchema.get("default")
            if (defaultValue != null) {
              defaultedNode.set<JsonNode>(key, defaultValue.deepCopy())
            }
          }
        }
      }

      defaultedNode is ArrayNode -> {
        val itemSchemaNode = resolvedSchema.get("items")
        if (itemSchemaNode != null) {
          for (index in 0 until defaultedNode.size()) {
            defaultedNode.set(index, applyDefaults(defaultedNode.get(index), itemSchemaNode, rootSchemaNode))
          }
        }
      }
    }

    return defaultedNode
  }

  private fun resolveSchema(schemaNode: JsonNode, rootSchemaNode: JsonNode): JsonNode {
    val ref = schemaNode.get("\$ref")?.asText()?.trim()
    if (ref.isNullOrBlank()) return schemaNode
    if (!ref.startsWith("#")) return schemaNode
    val pointer = ref.removePrefix("#")
    if (pointer.isBlank()) return rootSchemaNode
    val resolved = rootSchemaNode.at(pointer)
    return if (resolved.isMissingNode) schemaNode else resolved
  }

  private fun normalizePath(path: String): String = if (path.isBlank()) "$" else path
}

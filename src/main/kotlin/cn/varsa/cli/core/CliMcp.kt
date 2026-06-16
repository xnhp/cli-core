package cn.varsa.cli.core

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.collections.buildList

data class CliToolBinding(
  val id: String,
  val title: String? = null,
  val description: String? = null,
  val inputSchema: ToolSchema? = null,
  val annotations: ToolAnnotations? = null,
  val decodeArguments: (JsonObject?) -> Array<String> = ::decodeArgsArray,
  val executor: (suspend CliToolExecutionContext.() -> CallToolResult)? = null
) {
  companion object {
    internal val DEFAULT_ARGS_ARRAY_SCHEMA = ToolSchema(
      properties = buildJsonObject {
        put("args", buildJsonObject {
          put("type", JsonPrimitive("array"))
          put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
          put("description", JsonPrimitive("Command-line style arguments for the CLI command"))
        })
      }
    )

    internal fun decodeArgsArray(arguments: JsonObject?): Array<String> {
      val argsNode = arguments?.get("args")
      if (argsNode == null || argsNode !is JsonArray) return emptyArray()
      return argsNode.map { element ->
        element.jsonPrimitive.content
      }.toTypedArray()
    }

    fun decodeGeneratedSchema(leaf: CliCommandLeaf, arguments: JsonObject?): Array<String> {
      if (arguments == null || arguments.containsKey("args")) return decodeArgsArray(arguments)
      return buildList {
        leaf.options.forEach { option ->
          val value = arguments[option.schemaName()] ?: return@forEach
          if (option.takesValue) {
            add(option.names.first())
            add(value.jsonPrimitive.content)
          } else if (value.jsonPrimitive.booleanOrNull == true) {
            add(option.names.first())
          }
        }
        leaf.positionalArgs.sortedBy { it.index }.forEach { arg ->
          when (val value = arguments[arg.name]) {
            is JsonArray -> value.forEach { add(it.jsonPrimitive.content) }
            null -> Unit
            else -> add(value.jsonPrimitive.content)
          }
        }
      }.toTypedArray()
    }
  }
}

data class CliToolExecutionContext(
  val request: CallToolRequest,
  val commandArgs: Array<String>,
  val commandPath: List<String>,
  val leaf: CliCommandLeaf,
  val binding: CliToolBinding,
  val root: CliCommandGroup
)

data class CliRegisteredTool(
  val name: String,
  val title: String?,
  val description: String,
  val inputSchema: ToolSchema,
  val annotations: ToolAnnotations?,
  internal val leaf: CliCommandLeaf,
  internal val binding: CliToolBinding,
  internal val commandPath: List<String>
)

data class CliMcpRegistrationConfig(
  val workingDirectoryProvider: (() -> Path)? = null,
  val beforeInvoke: (() -> Unit)? = null,
  val afterInvoke: (() -> Unit)? = null
)

private val cliMcpExecutionLock = ReentrantLock()

fun Server.registerCliTools(
  root: CliCommandGroup,
  config: CliMcpRegistrationConfig = CliMcpRegistrationConfig()
) {
  root.cliMcpTools().forEach { tool ->
    addTool(
      name = tool.name,
      description = tool.description,
      inputSchema = tool.inputSchema,
      title = tool.title,
      toolAnnotations = tool.annotations
    ) { request ->
      executeCliTool(root, tool, request, config)
    }
  }
}

fun createCliMcpServer(
  root: CliCommandGroup,
  name: String = root.name,
  version: String = "0.1.0",
  instructions: String = "Tools are generated from explicitly annotated cli-core command leaves.",
  config: CliMcpRegistrationConfig = CliMcpRegistrationConfig()
): Server = Server(
  Implementation(name = name, version = version),
  ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))),
  instructions,
  {
    registerCliTools(root, config)
  }
)

suspend fun runCliMcpStdioServer(
  root: CliCommandGroup,
  name: String = root.name,
  version: String = "0.1.0",
  instructions: String = "Tools are generated from explicitly annotated cli-core command leaves.",
  config: CliMcpRegistrationConfig = CliMcpRegistrationConfig()
) {
  val protocolOut = System.out
  val server = withStdoutRedirectedToStderr {
    createCliMcpServer(root, name, version, instructions, config)
  }
  server.createSession(
    StdioServerTransport(
      System.`in`.asSource().buffered(),
      protocolOut.asSink().buffered()
    ) {}
  )
  awaitCancellation()
}

private inline fun <T> withStdoutRedirectedToStderr(block: () -> T): T {
  val originalOut = System.out
  val originalErr = System.err
  System.setOut(PrintStream(object : OutputStream() {
    override fun write(b: Int) {
      originalErr.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      originalErr.write(b, off, len)
    }

    override fun flush() {
      originalErr.flush()
    }
  }, true, StandardCharsets.UTF_8))
  return try {
    block()
  } finally {
    System.setOut(originalOut)
  }
}

fun CliCommandGroup.cliMcpTools(): List<CliRegisteredTool> = buildList {
  collectCliMcpTools(this@cliMcpTools, listOf(name))
}

private fun MutableList<CliRegisteredTool>.collectCliMcpTools(
  node: CliCommandNode,
  path: List<String>
) {
  when (node) {
    is CliCommandGroup -> node.children.forEach { child ->
      collectCliMcpTools(child, path + child.name)
    }

    is CliCommandLeaf -> {
      val binding = node.tool ?: return
      val toolDescription = binding.description ?: node.description
      add(
        CliRegisteredTool(
          name = binding.id,
          inputSchema = binding.inputSchema ?: node.toToolSchema(),
          title = binding.title,
          description = toolDescription,
          annotations = binding.annotations,
          leaf = node,
          binding = binding,
          commandPath = path.drop(1)
        )
      )
    }
  }
}

suspend fun executeCliTool(
  root: CliCommandGroup,
  tool: CliRegisteredTool,
  request: CallToolRequest,
  config: CliMcpRegistrationConfig = CliMcpRegistrationConfig()
): CallToolResult {
  val cliArgs = if (tool.binding.inputSchema == null) {
    CliToolBinding.decodeGeneratedSchema(tool.leaf, request.params.arguments)
  } else {
    tool.binding.decodeArguments(request.params.arguments)
  }
  val commandArgs = (tool.commandPath + cliArgs.toList()).toTypedArray()
  tool.binding.executor?.let { executor ->
    val context = CliToolExecutionContext(
      request = request,
      commandArgs = commandArgs,
      commandPath = tool.commandPath,
      leaf = tool.leaf,
      binding = tool.binding,
      root = root
    )
    return executor.invoke(context)
  }
  return executeCliCommand(root, commandArgs, config)
}

private fun CliCommandLeaf.toToolSchema(): ToolSchema {
  if (options.isEmpty() && positionalArgs.isEmpty()) return CliToolBinding.DEFAULT_ARGS_ARRAY_SCHEMA
  return ToolSchema(
    properties = buildJsonObject {
      options.forEach { option ->
        put(option.schemaName(), option.toSchemaProperty())
      }
      positionalArgs.sortedBy { it.index }.forEach { arg ->
        put(arg.name, arg.toSchemaProperty())
      }
    },
    required = (options.filter { it.required }.map { it.schemaName() } +
      positionalArgs.filter { it.arity.isRequiredArity() }.map { it.name }).ifEmpty { null }
  )
}

private fun CliOption.schemaName(): String = names
  .firstOrNull { it.startsWith("--") }
  ?.removePrefix("--")
  ?: names.first().trimStart('-')

private fun CliOption.toSchemaProperty(): JsonElement = buildJsonObject {
  put("type", JsonPrimitive(if (takesValue) "string" else "boolean"))
  put("description", JsonPrimitive(description))
  valueLabel?.let { put("title", JsonPrimitive(it)) }
  defaultValue?.let { put("default", JsonPrimitive(it)) }
}

private fun CliPositionalArg.toSchemaProperty(): JsonElement = buildJsonObject {
  put("type", JsonPrimitive(if (arity.isMultipleArity()) "array" else "string"))
  if (arity.isMultipleArity()) {
    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
  }
  put("description", JsonPrimitive(description))
}

private fun String.isRequiredArity(): Boolean {
  val minimum = substringBefore("..").substringBefore("*").toIntOrNull()
  return minimum != null && minimum > 0
}

private fun String.isMultipleArity(): Boolean = contains("..") || contains("*")

private fun executeCliCommand(
  root: CliCommandGroup,
  commandArgs: Array<String>,
  config: CliMcpRegistrationConfig
): CallToolResult = cliMcpExecutionLock.withLock {
  val stdoutBuffer = ByteArrayOutputStream()
  val stderrBuffer = ByteArrayOutputStream()
  val stdoutStream = PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8)
  val stderrStream = PrintStream(stderrBuffer, true, StandardCharsets.UTF_8)
  val stdoutWriter = PrintWriter(stdoutStream, true)
  val stderrWriter = PrintWriter(stderrStream, true)

  val originalOut = System.out
  val originalErr = System.err
  val originalUserDir = System.getProperty("user.dir")

  val commandLine = CliMain.createCommandLine(root).apply {
    setOut(stdoutWriter)
    setErr(stderrWriter)
  }

  val workingDir = runCatching { config.workingDirectoryProvider?.invoke() }.getOrNull()
  if (workingDir != null) {
    System.setProperty("user.dir", workingDir.toString())
  }

  config.beforeInvoke?.invoke()

  val exitCode: Int = try {
    System.setOut(stdoutStream)
    System.setErr(stderrStream)
    commandLine.execute(*commandArgs)
  } finally {
    stdoutWriter.flush()
    stderrWriter.flush()
    System.setOut(originalOut)
    System.setErr(originalErr)
    if (originalUserDir != null) {
      System.setProperty("user.dir", originalUserDir)
    }
    config.afterInvoke?.invoke()
  }

  val stdout = stdoutBuffer.toString(StandardCharsets.UTF_8)
  val stderr = stderrBuffer.toString(StandardCharsets.UTF_8)

  val structured = buildJsonObject {
    put("exitCode", JsonPrimitive(exitCode))
    put("stdout", JsonPrimitive(stdout))
    put("stderr", JsonPrimitive(stderr))
  }

  val contentBlocks = buildList {
    if (stdout.isNotBlank()) add(TextContent(stdout.trimEnd()))
    if (stderr.isNotBlank()) add(TextContent(stderr.trimEnd()))
    if (isEmpty()) add(TextContent("Command completed with exit code $exitCode"))
  }

  return if (exitCode == 0) {
    CallToolResult(
      content = contentBlocks,
      isError = false,
      structuredContent = structured
    )
  } else {
    CallToolResult(
      content = contentBlocks,
      isError = true,
      structuredContent = structured
    )
  }
}

package cn.varsa.cli.core

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

enum class ColorMode {
  AUTO,
  ALWAYS,
  NEVER;

  companion object {
    fun parse(value: String?): ColorMode = when (value?.lowercase()) {
      "always" -> ALWAYS
      "never" -> NEVER
      else -> AUTO
    }
  }
}

enum class CliLogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG,
  TRACE;

  fun toJul(): Level = when (this) {
    ERROR -> Level.SEVERE
    WARN -> Level.WARNING
    INFO -> Level.INFO
    DEBUG -> Level.FINE
    TRACE -> Level.FINEST
  }

  companion object {
    fun parse(value: String?): CliLogLevel = when (value?.lowercase()) {
      "error", "severe" -> ERROR
      "warn", "warning" -> WARN
      "info" -> INFO
      "debug", "fine" -> DEBUG
      "trace", "finest" -> TRACE
      else -> WARN
    }

    fun resolve(explicit: String?, verbose: Boolean, debug: Boolean): CliLogLevel = when {
      explicit != null -> parse(explicit)
      debug -> DEBUG
      verbose -> INFO
      else -> WARN
    }
  }
}

open class CliFailure(message: String, val exitCode: Int = 1, cause: Throwable? = null) : RuntimeException(message, cause)

class CliException(message: String, exitCode: Int = 1, cause: Throwable? = null) : CliFailure(message, exitCode, cause)

object CliStyle {
  fun useColor(mode: ColorMode): Boolean = when (mode) {
    ColorMode.ALWAYS -> true
    ColorMode.NEVER -> false
    ColorMode.AUTO -> System.console() != null
  }

  fun info(useColor: Boolean): String = label("INFO", "34", useColor)
  fun warn(useColor: Boolean): String = label("WARN", "33", useColor)
  fun error(useColor: Boolean): String = label("ERROR", "31", useColor)
  fun debug(useColor: Boolean): String = label("DEBUG", "36", useColor)
  fun trace(useColor: Boolean): String = label("TRACE", "35", useColor)
  fun success(text: String, useColor: Boolean): String = colorize(text, "32", useColor)
  fun danger(text: String, useColor: Boolean): String = colorize(text, "31", useColor)
  fun surfaceMuted(text: String, useColor: Boolean): String = colorize(text, "90", useColor)
  fun surfaceDark(text: String, useColor: Boolean): String = colorize(text, "38;5;240", useColor)
  fun bold(text: String, useColor: Boolean): String = if (useColor) "\u001B[1m$text\u001B[0m" else text
  fun warnPrefix(useColor: Boolean): String = colorize("Warning:", "33", useColor)

  fun maturityTag(label: String, useColor: Boolean): String = when (label.lowercase()) {
    "usable" -> success(label, useColor)
    "wip" -> danger(label, useColor)
    else -> "[$label]"
  }

  private fun label(text: String, colorCode: String, useColor: Boolean): String =
    colorize("[$text]", colorCode, useColor)

  private fun colorize(text: String, colorCode: String, useColor: Boolean): String {
    if (!useColor) return text
    return "\u001B[${colorCode}m$text\u001B[0m"
  }
}

object CliLogging {
  fun configure(level: CliLogLevel, useColor: Boolean) {
    val julLevel = level.toJul()
    val root = Logger.getLogger("")
    root.level = julLevel
    val formatter = object : Formatter() {
      override fun format(record: LogRecord): String {
        val message = formatMessage(record)
        val prefix = when {
          record.level.intValue() >= Level.SEVERE.intValue() -> CliStyle.error(useColor)
          record.level.intValue() >= Level.WARNING.intValue() -> CliStyle.warn(useColor)
          record.level.intValue() >= Level.INFO.intValue() -> CliStyle.info(useColor)
          record.level.intValue() >= Level.FINE.intValue() -> CliStyle.debug(useColor)
          else -> CliStyle.trace(useColor)
        }
        return "$prefix $message\n"
      }
    }
    root.handlers?.forEach { handler ->
      handler.level = julLevel
      handler.formatter = formatter
    }
  }
}

object CliProcess {
  fun runCapture(workingDir: Path, command: List<String>, errorMessage: String): String {
    val process = ProcessBuilder(command)
      .directory(workingDir.toFile())
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      val details = if (output.isBlank()) "" else "\n$output"
      throw CliFailure("$errorMessage.$details")
    }
    return output
  }

  fun runStreaming(workingDir: Path, command: List<String>, errorMessage: String) {
    val process = ProcessBuilder(command)
      .directory(workingDir.toFile())
      .redirectErrorStream(true)
      .start()
    val output = StringBuilder()
    process.inputStream.bufferedReader().useLines { lines ->
      lines.forEach { line ->
        println(line)
        output.appendLine(line)
      }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      val details = if (output.isBlank()) "" else "\n${output.toString().trimEnd()}"
      throw CliFailure("$errorMessage.$details")
    }
  }
}

sealed interface CliCommandNode {
  val name: String
  val description: String
  val aliases: List<String>
}

data class CliCommandGroup(
  override val name: String,
  override val description: String,
  val children: List<CliCommandNode>,
  val handler: (() -> Int)? = null,
  override val aliases: List<String> = emptyList(),
  val mixinStandardHelpOptions: Boolean = true
) : CliCommandNode

data class CliCommandLeaf(
  override val name: String,
  override val description: String,
  val handler: (Array<String>) -> Int,
  override val aliases: List<String> = emptyList(),
  val mixinStandardHelpOptions: Boolean = false,
  val options: List<CliOption> = emptyList(),
  val positionalArgs: List<CliPositionalArg> = emptyList(),
  val tool: CliToolBinding? = null
) : CliCommandNode

data class CliOption(
  val names: List<String>,
  val description: String,
  val takesValue: Boolean = false,
  val valueLabel: String? = null,
  val required: Boolean = false,
  val defaultValue: String? = null,
  val arity: String? = null,
  val repeatable: Boolean = false
)

data class CliPositionalArg(
  val index: Int,
  val name: String,
  val description: String,
  val arity: String = "0..1"
)

object CliArgs {
  fun requireArgCount(args: Array<String>, expected: Int, usage: String, exitCode: Int = 2) {
    requireArgCount(args, expected..expected, usage, exitCode)
  }

  fun requireArgCount(args: Array<String>, allowed: IntRange, usage: String, exitCode: Int = 2) {
    if (args.size !in allowed) throw CliException("Usage: $usage", exitCode)
  }

  fun singleFlag(args: Array<String>, flag: String, usage: String, exitCode: Int = 2): Boolean = when {
    args.isEmpty() -> false
    args.size == 1 && args[0] == flag -> true
    else -> throw CliException("Usage: $usage", exitCode)
  }
}

object CliDsl {
  fun group(
    name: String,
    description: String,
    children: List<CliCommandNode>,
    handler: (() -> Int)? = null,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = true
  ): CliCommandGroup = CliCommandGroup(
    name = name,
    description = description,
    children = children,
    handler = handler,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions
  )

  fun group(
    name: String,
    description: String,
    vararg children: CliCommandNode
  ): CliCommandGroup = group(name, description, children.toList())

  fun action(
    name: String,
    description: String,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    options: List<CliOption> = emptyList(),
    positionalArgs: List<CliPositionalArg> = emptyList(),
    tool: CliToolBinding? = null,
    handler: (Array<String>) -> Unit
  ): CliCommandLeaf = CliCommandLeaf(
    name = name,
    description = description,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    options = options,
    positionalArgs = positionalArgs,
    tool = tool,
    handler = { args ->
      handler(args)
      0
    }
  )

  fun output(
    name: String,
    description: String,
    print: (String) -> Unit,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    options: List<CliOption> = emptyList(),
    positionalArgs: List<CliPositionalArg> = emptyList(),
    tool: CliToolBinding? = null,
    handler: (Array<String>) -> String
  ): CliCommandLeaf = CliCommandLeaf(
    name = name,
    description = description,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    options = options,
    positionalArgs = positionalArgs,
    tool = tool,
    handler = { args ->
      print(handler(args))
      0
    }
  )

  fun actionNoArgs(
    name: String,
    description: String,
    usage: String,
    exitCode: Int = 2,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    tool: CliToolBinding? = null,
    handler: () -> Unit
  ): CliCommandLeaf = action(
    name = name,
    description = description,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    tool = tool
  ) { args ->
    CliArgs.requireArgCount(args, 0, usage, exitCode)
    handler()
  }

  fun outputNoArgs(
    name: String,
    description: String,
    usage: String,
    print: (String) -> Unit,
    exitCode: Int = 2,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    tool: CliToolBinding? = null,
    handler: () -> String
  ): CliCommandLeaf = output(
    name = name,
    description = description,
    print = print,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    tool = tool
  ) { args ->
    CliArgs.requireArgCount(args, 0, usage, exitCode)
    handler()
  }

  fun <T> parsedAction(
    name: String,
    description: String,
    parse: (Array<String>) -> T,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    options: List<CliOption> = emptyList(),
    positionalArgs: List<CliPositionalArg> = emptyList(),
    tool: CliToolBinding? = null,
    handler: (T) -> Unit
  ): CliCommandLeaf = action(
    name = name,
    description = description,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    options = options,
    positionalArgs = positionalArgs,
    tool = tool
  ) { args ->
    handler(parse(args))
  }

  fun <T> parsedOutput(
    name: String,
    description: String,
    print: (String) -> Unit,
    parse: (Array<String>) -> T,
    aliases: List<String> = emptyList(),
    mixinStandardHelpOptions: Boolean = false,
    options: List<CliOption> = emptyList(),
    positionalArgs: List<CliPositionalArg> = emptyList(),
    tool: CliToolBinding? = null,
    handler: (T) -> String
  ): CliCommandLeaf = output(
    name = name,
    description = description,
    print = print,
    aliases = aliases,
    mixinStandardHelpOptions = mixinStandardHelpOptions,
    options = options,
    positionalArgs = positionalArgs,
    tool = tool
  ) { args ->
    handler(parse(args))
  }
}

object CliCompletion {
  fun suggest(root: CliCommandGroup, tokens: List<String>): List<String> {
    val args = if (tokens.firstOrNull() == root.name) tokens.drop(1) else tokens
    val path = args.filter { it.isNotBlank() }
    val node = resolveNode(root, path)
    return when (node) {
      is CliCommandGroup -> node.children
        .asSequence()
        .filterNot { it.name.startsWith("__") }
        .flatMap { child -> sequenceOf(child.name) + child.aliases.asSequence() }
        .distinct()
        .sorted()
        .toList()
      is CliCommandLeaf -> node.options
        .flatMap { it.names }
        .distinct()
        .sorted()
    }
  }

  fun zshScript(commandName: String): String = """
    #compdef $commandName

    _$commandName() {
      local -a context_words
      if (( CURRENT > 1 )); then
        context_words=("${'$'}{(@)words[1,${'$'}((CURRENT-1))]}")
      else
        context_words=()
      fi

      local -a suggestions
      suggestions=("${'$'}{(@f)${'$'}($commandName __complete "${'$'}{context_words[@]}")}")
      if (( ${'$'}{#suggestions[@]} > 0 )); then
        compadd -- "${'$'}{suggestions[@]}"
      fi
    }

    compdef _$commandName $commandName
  """.trimIndent()

  private fun resolveNode(root: CliCommandGroup, path: List<String>): CliCommandNode {
    var current: CliCommandNode = root
    for (token in path) {
      if (token.startsWith("-")) continue
      val group = current as? CliCommandGroup ?: return current
      val next = group.children.firstOrNull { child ->
        child.name == token || child.aliases.contains(token)
      } ?: return group
      current = next
    }
    return current
  }
}

@CommandLine.Command
private class CliGroupExecutor(private val handler: (() -> Int)?) : Callable<Int> {
  @CommandLine.Spec
  lateinit var spec: CommandSpec

  override fun call(): Int {
    if (handler != null) return handler.invoke()
    spec.commandLine().usage(System.out)
    return 0
  }
}

@CommandLine.Command
private class CliLeafExecutor(private val handler: (Array<String>) -> Int) : Callable<Int> {
  @CommandLine.Spec
  lateinit var spec: CommandSpec

  @CommandLine.Unmatched
  var args: MutableList<String> = mutableListOf()

  override fun call(): Int {
    val original = spec.commandLine().parseResult
      ?.originalArgs()
      ?.map { it.toString() }
      ?.toTypedArray()
    val effective = original?.let { stripCommandPathTokens(it, spec) } ?: args.toTypedArray()
    return handler(effective)
  }
}

private fun stripCommandPathTokens(args: Array<String>, spec: CommandSpec): Array<String> {
  val tokens = args.toList()
  val path = generateSequence(spec) { it.parent() }
    .map { it.name() }
    .toList()
    .asReversed()
  val withoutRoot = if (path.size > 1) path.drop(1) else emptyList()
  return when {
    tokens.startsWith(path) -> tokens.drop(path.size).toTypedArray()
    withoutRoot.isNotEmpty() && tokens.startsWith(withoutRoot) -> tokens.drop(withoutRoot.size).toTypedArray()
    else -> args
  }
}

private fun List<String>.startsWith(prefix: List<String>): Boolean {
  if (prefix.size > this.size) return false
  return prefix.indices.all { this[it] == prefix[it] }
}

private fun createCommandLine(node: CliCommandNode): CommandLine {
  val commandLine = when (node) {
    is CliCommandGroup -> CommandLine(CliGroupExecutor(node.handler))
    is CliCommandLeaf -> CommandLine(CliLeafExecutor(node.handler))
  }

  val spec = commandLine.commandSpec
  spec.name(node.name)
  spec.usageMessage().description(node.description)
  if (node.aliases.isNotEmpty()) {
    spec.aliases(*node.aliases.toTypedArray())
  }

  val mixinHelp = when (node) {
    is CliCommandGroup -> node.mixinStandardHelpOptions
    is CliCommandLeaf -> node.mixinStandardHelpOptions
  }
  spec.mixinStandardHelpOptions(mixinHelp)

  if (node is CliCommandLeaf) {
    node.options.forEach { option ->
      val builder = CommandLine.Model.OptionSpec.builder(option.names.toTypedArray())
      builder.description(option.description)
      builder.required(option.required)
      option.defaultValue?.let { value -> builder.defaultValue(value) }
      when {
        option.arity != null -> builder.arity(option.arity)
        option.takesValue -> builder.arity("1")
        else -> builder.arity("0")
      }
      if (option.takesValue) {
        if (option.repeatable) {
          builder.type(List::class.java).auxiliaryTypes(String::class.java)
        } else {
          builder.type(String::class.java)
        }
        option.valueLabel?.let { label -> builder.paramLabel(label) }
      }
      spec.addOption(builder.build())
    }
    node.positionalArgs.sortedBy { it.index }.forEach { arg ->
      val builder = CommandLine.Model.PositionalParamSpec.builder()
      builder.index(arg.index.toString())
      builder.paramLabel(arg.name)
      builder.arity(arg.arity)
      builder.description(arg.description)
      builder.type(String::class.java)
      spec.addPositional(builder.build())
    }
    commandLine.setUnmatchedArgumentsAllowed(true)
    commandLine.setUnmatchedOptionsArePositionalParams(true)
  }

  if (node is CliCommandGroup) {
    node.children.forEach { child ->
      val childCommand = createCommandLine(child)
      commandLine.addSubcommand(childCommand.commandSpec.name(), childCommand)
    }
  }

  return commandLine
}

object CliMain {
  fun createCommandLine(rootCommand: CliCommandGroup): CommandLine = createCommandLine(rootCommand as CliCommandNode)

  fun run(rootCommand: CliCommandGroup, args: Array<String>): Int {
    val commandLine = createCommandLine(rootCommand)
    return run(commandLine, args)
  }

  fun run(rootCommand: Any, args: Array<String>): Int {
    val commandLine = CommandLine(rootCommand)
    return run(commandLine, args)
  }

  fun run(commandLine: CommandLine, args: Array<String>): Int {
    commandLine.setExecutionExceptionHandler { ex, cmd, _ ->
      val useColor = CliStyle.useColor(ColorMode.AUTO)
      val prefix = CliStyle.error(useColor)
      if (ex is CliFailure) {
        cmd.err.println("$prefix ${ex.message}")
        return@setExecutionExceptionHandler ex.exitCode
      }
      cmd.err.println("$prefix ${ex.message}")
      1
    }
    return commandLine.execute(*args)
  }
}

object CliCommands {
  fun discover(rootCommand: Any): List<CommandLine> = discover(CommandLine(rootCommand))

  fun discover(rootCommandLine: CommandLine): List<CommandLine> {
    val result = mutableListOf<CommandLine>()

    fun visit(commandLine: CommandLine) {
      result.add(commandLine)
      val subcommands: Map<String, CommandLine> = commandLine.subcommands
      val children = subcommands
        .values
        .distinctBy { System.identityHashCode(it) }
        .sortedBy { it.commandSpec.name() }
      children.forEach(::visit)
    }

    visit(rootCommandLine)
    return result
  }

  fun commandPath(commandLine: CommandLine): String {
    val names = mutableListOf<String>()
    var spec: CommandSpec = commandLine.commandSpec
    while (true) {
      names.add(spec.name())
      val parent = spec.parent() ?: break
      spec = parent
    }
    return names.asReversed().joinToString(" ")
  }
}

@Suppress("unused")
internal fun callableOf(block: () -> Int): Callable<Int> = Callable { block() }

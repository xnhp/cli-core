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

object CliMain {
  fun run(rootCommand: Any, args: Array<String>): Int {
    val commandLine = CommandLine(rootCommand)
    commandLine.setExecutionExceptionHandler { ex, cmd, _ ->
      if (ex is CliFailure) {
        cmd.err.println("Error: ${ex.message}")
        return@setExecutionExceptionHandler ex.exitCode
      }
      cmd.err.println("Error: ${ex.message}")
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

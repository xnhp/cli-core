package cn.varsa.cli.core

import java.nio.file.Path

data class CliLineRange(val start: Int, val end: Int) {
  init {
    require(start >= 1) { "Line range start must be >= 1" }
    require(end >= start) { "Line range end must be >= start" }
  }
}

data class CliPathLineRanges(val path: Path, val ranges: List<CliLineRange> = emptyList())

object CliPathLineRangeFormat {
  private val rangeListRegex = Regex("\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*")

  fun format(path: Path, ranges: List<CliLineRange>): String {
    if (ranges.isEmpty()) return path.toString()
    return "${path}:${formatRanges(ranges)}"
  }

  fun parse(line: String): CliPathLineRanges {
    val trimmed = line.trim()
    val separator = trimmed.lastIndexOf(':')
    if (separator < 0) return CliPathLineRanges(Path.of(trimmed))

    val suffix = trimmed.substring(separator + 1)
    if (!rangeListRegex.matches(suffix)) return CliPathLineRanges(Path.of(trimmed))

    return CliPathLineRanges(
      path = Path.of(trimmed.substring(0, separator)),
      ranges = parseRanges(suffix)
    )
  }

  fun parseRanges(value: String): List<CliLineRange> {
    if (!rangeListRegex.matches(value)) throw IllegalArgumentException("Invalid line ranges: $value")
    return value.split(',').map { part ->
      val bounds = part.split('-', limit = 2)
      val start = bounds[0].toInt()
      val end = bounds.getOrNull(1)?.toInt() ?: start
      CliLineRange(start, end)
    }
  }

  fun formatRanges(ranges: List<CliLineRange>): String = ranges.joinToString(",") { range ->
    if (range.start == range.end) range.start.toString() else "${range.start}-${range.end}"
  }

  fun mergeAdjacent(ranges: List<CliLineRange>): List<CliLineRange> {
    val sorted = ranges.sortedWith(compareBy<CliLineRange> { it.start }.thenBy { it.end })
    if (sorted.isEmpty()) return emptyList()

    val merged = mutableListOf<CliLineRange>()
    for (range in sorted) {
      val previous = merged.lastOrNull()
      if (previous != null && range.start <= previous.end + 1) {
        merged[merged.lastIndex] = CliLineRange(previous.start, maxOf(previous.end, range.end))
      } else {
        merged.add(range)
      }
    }
    return merged
  }
}

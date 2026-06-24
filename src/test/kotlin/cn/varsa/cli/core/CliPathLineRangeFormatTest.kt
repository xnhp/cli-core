package cn.varsa.cli.core

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class CliPathLineRangeFormatTest {
  @Test
  fun `formats path with ranges`() {
    assertEquals(
      "/repo/A.java:1,3-5",
      CliPathLineRangeFormat.format(Path("/repo/A.java"), listOf(CliLineRange(1, 1), CliLineRange(3, 5)))
    )
  }

  @Test
  fun `parses path with ranges`() {
    assertEquals(
      CliPathLineRanges(Path("/repo/A.java"), listOf(CliLineRange(1, 1), CliLineRange(3, 5))),
      CliPathLineRangeFormat.parse("/repo/A.java:1,3-5")
    )
  }

  @Test
  fun `leaves colon path without range suffix untouched`() {
    assertEquals(
      CliPathLineRanges(Path("/repo/A:copy.java")),
      CliPathLineRangeFormat.parse("/repo/A:copy.java")
    )
  }

  @Test
  fun `merges adjacent ranges`() {
    assertEquals(
      listOf(CliLineRange(1, 5), CliLineRange(8, 8)),
      CliPathLineRangeFormat.mergeAdjacent(listOf(CliLineRange(3, 5), CliLineRange(1, 2), CliLineRange(8, 8)))
    )
  }
}

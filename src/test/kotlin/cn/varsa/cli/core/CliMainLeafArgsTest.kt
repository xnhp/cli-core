package cn.varsa.cli.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CliMainLeafArgsTest {

  @Test
  fun `surface styles render with theme colors`() {
    assertEquals("\u001B[90m(author)\u001B[0m", CliStyle.surfaceMuted("(author)", useColor = true))
    assertEquals("\u001B[38;5;240msource → target\u001B[0m", CliStyle.surfaceDark("source → target", useColor = true))
    assertEquals("source → target", CliStyle.surfaceDark("source → target", useColor = false))
  }

  @Test
  fun `leaf handler receives args without command path`() {
    var captured: Array<String> = emptyArray()
    val root = CliCommandGroup(
      name = "pde",
      description = "root",
      children = listOf(
        CliCommandLeaf(
          name = "test",
          description = "test",
          handler = { args ->
            captured = args
            0
          },
          mixinStandardHelpOptions = true,
          options = listOf(CliOption(listOf("--all"), "run all")),
          positionalArgs = listOf(CliPositionalArg(0, "name", "name", "0..1"))
        )
      )
    )

    CliMain.run(root, arrayOf("test", "--all", "sample"))

    assertContentEquals(arrayOf("--all", "sample"), captured)
  }
}

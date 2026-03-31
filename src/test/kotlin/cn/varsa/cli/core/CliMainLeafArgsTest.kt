package cn.varsa.cli.core

import kotlin.test.Test
import kotlin.test.assertContentEquals

class CliMainLeafArgsTest {

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

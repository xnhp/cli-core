package cn.varsa.cli.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CliDslTest {
  @Test
  fun `parsed output leaf parses then prints`() {
    val printed = mutableListOf<String>()
    val root = CliDsl.group(
      name = "app",
      description = "root",
      children = listOf(
        CliDsl.parsedOutput(
          name = "sum",
          description = "sum",
          print = { printed += it },
          parse = { args ->
            CliArgs.requireArgCount(args, 2, "app sum <a> <b>")
            args[0].toInt() to args[1].toInt()
          }
        ) { (a, b) ->
          (a + b).toString()
        }
      )
    )

    val exitCode = CliMain.run(root, arrayOf("sum", "2", "3"))

    assertEquals(0, exitCode)
    assertEquals(listOf("5"), printed)
  }

  @Test
  fun `single flag helper accepts only exact form`() {
    assertEquals(false, CliArgs.singleFlag(emptyArray(), "--json", "cmd [--json]"))
    assertEquals(true, CliArgs.singleFlag(arrayOf("--json"), "--json", "cmd [--json]"))
    assertFailsWith<CliException> {
      CliArgs.singleFlag(arrayOf("--json", "extra"), "--json", "cmd [--json]")
    }
  }
}

package cn.varsa.cli.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliCompletionTest {
  @Test
  fun `suggest returns subcommands for command path`() {
    val root = CliDsl.group(
      name = "egg",
      description = "root",
      children = listOf(
        CliDsl.group(
          name = "gh",
          description = "gh",
          children = listOf(
            CliDsl.action(name = "search", description = "search") { }
          )
        ),
        CliDsl.action(name = "__complete", description = "internal") { }
      )
    )

    val top = CliCompletion.suggest(root, listOf("egg"))
    val gh = CliCompletion.suggest(root, listOf("egg", "gh"))

    assertEquals(listOf("gh"), top)
    assertEquals(listOf("search"), gh)
  }

  @Test
  fun `suggest returns leaf option names`() {
    val root = CliDsl.group(
      name = "app",
      description = "root",
      children = listOf(
        CliCommandLeaf(
          name = "run",
          description = "run",
          handler = { 0 },
          options = listOf(CliOption(names = listOf("--json", "-j"), description = "json"))
        )
      )
    )

    val options = CliCompletion.suggest(root, listOf("app", "run"))

    assertEquals(listOf("--json", "-j"), options)
  }

  @Test
  fun `zsh script includes compdef and internal completion command`() {
    val script = CliCompletion.zshScript("egg")

    assertTrue(script.contains("#compdef egg"))
    assertTrue(script.contains("egg __complete"))
  }
}

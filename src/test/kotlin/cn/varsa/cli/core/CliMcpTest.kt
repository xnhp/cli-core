package cn.varsa.cli.core

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CliMcpTest {
  @Test
  fun `discovers explicitly annotated leaves as tools`() {
    val root = testRoot()

    val tools = root.cliMcpTools()

    assertEquals(listOf("greet-tool", "fail-tool"), tools.map { it.name })
    assertEquals("Say hello", tools.first().description)
  }

  @Test
  fun `generates schema from options and positionals`() {
    val tool = testRoot().cliMcpTools().first()
    val properties = tool.inputSchema.properties!!

    assertNotNull(properties["name"])
    assertNotNull(properties["shout"])
    assertNotNull(properties["target"])
    assertEquals("name", tool.inputSchema.required?.single())
  }

  @Test
  fun `successful invocation returns structured output`() = runBlocking {
    val root = testRoot()
    val tool = root.cliMcpTools().first()
    val request = request("greet-tool") {
      put("name", "Ada")
      put("target", "World")
    }

    val result = executeCliTool(root, tool, request)

    assertFalse(result.isError == true)
    val structured = result.structuredContent!!.jsonObject
    assertEquals("0", structured["exitCode"]!!.jsonPrimitive.content)
    assertTrue(structured["stdout"]!!.jsonPrimitive.content.contains("Hello Ada World"))
    assertEquals("", structured["stderr"]!!.jsonPrimitive.content)
  }

  @Test
  fun `failing invocation returns error with output context`() = runBlocking {
    val root = testRoot()
    val tool = root.cliMcpTools().single { it.name == "fail-tool" }

    val result = executeCliTool(root, tool, request("fail-tool"))

    assertTrue(result.isError == true)
    val structured = result.structuredContent!!.jsonObject
    assertEquals("7", structured["exitCode"]!!.jsonPrimitive.content)
    assertTrue(structured["stdout"]!!.jsonPrimitive.content.contains("before failure"))
    assertTrue(structured["stderr"]!!.jsonPrimitive.content.contains("boom"))
  }

  private fun testRoot(): CliCommandGroup = CliCommandGroup(
    name = "test",
    description = "Test root",
    children = listOf(
      CliCommandLeaf(
        name = "greet",
        description = "Say hello",
        options = listOf(
          CliOption(listOf("--name"), "Name to greet", takesValue = true, required = true),
          CliOption(listOf("--shout"), "Uppercase greeting")
        ),
        positionalArgs = listOf(CliPositionalArg(0, "target", "Greeting target", arity = "0..1")),
        tool = CliToolBinding("greet-tool"),
        handler = { args ->
          val name = args[args.indexOf("--name") + 1]
          val target = args.last()
          println("Hello $name $target")
          0
        }
      ),
      CliCommandLeaf(
        name = "fail",
        description = "Fail command",
        tool = CliToolBinding("fail-tool"),
        handler = {
          println("before failure")
          System.err.println("boom")
          7
        }
      )
    )
  )

  private fun request(name: String, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): CallToolRequest =
    CallToolRequest(CallToolRequestParams(name, buildJsonObject(block)))
}

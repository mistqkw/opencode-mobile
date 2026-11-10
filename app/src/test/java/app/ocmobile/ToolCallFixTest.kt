package app.ocmobile

import app.ocmobile.api.OcJson
import app.ocmobile.data.ToolCallFix
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #1: a streamed tool call that starts without an id broke the whole answer. */
class ToolCallFixTest {
    private fun call(line: String) = OcJson.parseToJsonElement(line.removePrefix("data: ")).jsonObject["choices"]!!
        .jsonArray[0].jsonObject["delta"]!!.jsonObject["tool_calls"]!!.jsonArray[0].jsonObject

    @Test fun firstChunkWithoutIdGetsOne() {
        val seen = HashSet<String>()
        val first = ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"bash","arguments":""}}]}}]}""", seen)
        val c = call(first)
        assertTrue(c["id"]!!.jsonPrimitive.content.startsWith("call_"))
        assertEquals("function", c["type"]!!.jsonPrimitive.content)
        // later chunks of the same call stay as they are: the SDK only checks the first one
        val next = """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}"""
        assertEquals(next, ToolCallFix.fixLine(next, seen))
    }

    @Test fun goodChunksPassThrough() {
        val seen = HashSet<String>()
        val ok = """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"bash","arguments":""}}]}}]}"""
        assertEquals(ok, ToolCallFix.fixLine(ok, seen))
        assertEquals("data: [DONE]", ToolCallFix.fixLine("data: [DONE]", seen))
        assertEquals(": keep-alive", ToolCallFix.fixLine(": keep-alive", seen))
    }

    @Test fun secondParallelCallGetsItsOwnId() {
        val seen = HashSet<String>()
        ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"a","type":"function","function":{"name":"read"}}]}}]}""", seen)
        val c = call(ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"name":"read"}}]}}]}""", seen))
        assertTrue(c["id"]!!.jsonPrimitive.content.startsWith("call_0_1"))
    }
}

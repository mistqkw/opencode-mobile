package app.ocmobile

import app.ocmobile.api.OcJson
import app.ocmobile.data.ToolCallFix
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #1: a streamed tool call that starts without an id (or a name) broke the whole answer. */
class ToolCallFixTest {
    private fun call(line: String) = OcJson.parseToJsonElement(line.removePrefix("data: ")).jsonObject["choices"]!!
        .jsonArray[0].jsonObject["delta"]!!.jsonObject["tool_calls"]!!.jsonArray[0].jsonObject

    @Test fun firstChunkWithoutIdGetsOne() {
        val seen = ToolCallFix.Stream()
        val first = ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"bash","arguments":""}}]}}]}""", seen)
        val c = call(first)
        assertTrue(c["id"]!!.jsonPrimitive.content.startsWith("call_"))
        assertEquals("function", c["type"]!!.jsonPrimitive.content)
        // later chunks of the same call stay as they are: the SDK only checks the first one
        val next = """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}"""
        assertEquals(next, ToolCallFix.fixLine(next, seen))
    }

    @Test fun goodChunksPassThrough() {
        val seen = ToolCallFix.Stream()
        val ok = """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"bash","arguments":""}}]}}]}"""
        assertEquals(ok, ToolCallFix.fixLine(ok, seen))
        assertEquals("data: [DONE]", ToolCallFix.fixLine("data: [DONE]", seen))
        assertEquals(": keep-alive", ToolCallFix.fixLine(": keep-alive", seen))
    }

    @Test fun secondParallelCallGetsItsOwnId() {
        val seen = ToolCallFix.Stream()
        ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"a","type":"function","function":{"name":"read"}}]}}]}""", seen)
        val c = call(ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"name":"read"}}]}}]}""", seen))
        assertTrue(c["id"]!!.jsonPrimitive.content.startsWith("call_0_1"))
    }

    private fun delta(line: String) = OcJson.parseToJsonElement(line.removePrefix("data: ")).jsonObject["choices"]!!
        .jsonArray[0].jsonObject["delta"]!!.jsonObject

    @Test fun callWithoutNameWaitsForIt() {
        val s = ToolCallFix.Stream()
        val first = ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"x","type":"function","function":{"arguments":"{\"file"}}]}}]}""", s)
        assertTrue("tool_calls" !in delta(first))
        val c = call(ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"read","arguments":"Path\":1}"}}]}}]}""", s))
        assertEquals("x", c["id"]!!.jsonPrimitive.content)
        assertEquals("read", c["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("{\"filePath\":1}", c["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }

    @Test fun callThatNeverGetsANameIsLeftOut() {
        val s = ToolCallFix.Stream()
        val first = ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"content":"ok","tool_calls":[{"index":0,"function":{"name":null,"arguments":"{}"}}]}}]}""", s)
        assertEquals("ok", delta(first)["content"]!!.jsonPrimitive.content)
        assertTrue("tool_calls" !in delta(first))
        val end = ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""", s)
        assertTrue(end.contains("\"finish_reason\":\"stop\""))
    }

    @Test fun finishStaysWhenACallWentOut() {
        val s = ToolCallFix.Stream()
        ToolCallFix.fixLine("""data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"a","type":"function","function":{"name":"read"}}]}}]}""", s)
        val end = """data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}"""
        assertEquals(end, ToolCallFix.fixLine(end, s))
    }

    @Test fun plainTextErrorBecomesReadable() {
        val e = OcJson.parseToJsonElement(ToolCallFix.errorBody("copilot-chat", 401, "unauthorized: token authentication failed")).jsonObject["error"]!!.jsonObject
        val msg = e["message"]!!.jsonPrimitive.content
        assertTrue(msg.startsWith("GitHub didn't accept the sign-in"))
        assertTrue(msg.endsWith("(401: unauthorized: token authentication failed)"))
    }

    @Test fun onlyYourOwnMessageCountsAsUser() {
        assertEquals("user", ToolCallFix.initiator("""{"messages":[{"role":"system","content":"x"},{"role":"user","content":"hi"}]}""".toByteArray()))
        assertEquals("agent", ToolCallFix.initiator("""{"messages":[{"role":"user","content":"hi"},{"role":"assistant","tool_calls":[]},{"role":"tool","content":"ok"}]}""".toByteArray()))
    }

    @Test fun copilotQuotaIsNamed() {
        val msg = OcJson.parseToJsonElement(ToolCallFix.errorBody("copilot-chat", 403, "You have exceeded your monthly chat limit")).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        assertTrue(msg.startsWith(ToolCallFix.COPILOT_CAPPED))
    }
}

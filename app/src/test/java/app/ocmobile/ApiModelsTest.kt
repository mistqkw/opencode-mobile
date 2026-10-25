package app.ocmobile

import app.ocmobile.api.AgentInfo
import app.ocmobile.api.OcJson
import app.ocmobile.api.ProvidersResponse
import app.ocmobile.api.Session
import app.ocmobile.api.obj
import app.ocmobile.api.parseMessages
import app.ocmobile.api.str
import app.ocmobile.runtime.TarExtractor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** Модели разбирают настоящие ответы OpenCode 1.19.4, снятые с живого сервера. */
class ApiModelsTest {
    private fun res(n: String) = javaClass.getResource("/fx/$n")!!.readText()

    @Test fun sessions() {
        val l = OcJson.decodeFromString(ListSerializer(Session.serializer()), res("sessions.json"))
        assertEquals(1, l.size)
        assertTrue(l[0].id.startsWith("ses_"))
        assertTrue(l[0].directory.endsWith("dirA"))
    }

    @Test fun messagesWithParts() {
        val m = parseMessages(OcJson.parseToJsonElement(res("messages.json")))
        assertEquals(2, m.size)
        assertTrue(m[0].info.isUser)
        assertEquals("text", m[0].parts[0].type)
        assertEquals("big-pickle", m[1].info.modelName)
    }

    @Test fun providers() {
        val r = OcJson.decodeFromString(ProvidersResponse.serializer(), res("providers.json"))
        val oc = r.providers.first { it.id == "opencode" }
        assertTrue(oc.models.containsKey("big-pickle"))
        assertTrue(oc.models["mimo-v2.6-flash-free"]!!.supportsImages)
        assertEquals("big-pickle", r.default["opencode"])
    }

    @Test fun agents() {
        val a = OcJson.decodeFromString(ListSerializer(AgentInfo.serializer()), res("agents.json"))
        assertTrue(a.any { it.name == "build" && !it.hidden })
        assertTrue(a.first { it.name == "title" }.hidden)
    }

    @Test fun eventEnvelope() {
        val lines = res("events.txt").lines().filter { it.startsWith("data:") }
        assertTrue(lines.isNotEmpty())
        for (l in lines) {
            val o = OcJson.parseToJsonElement(l.removePrefix("data: ")) as JsonObject
            val payload = o.obj("payload")!!
            assertTrue(payload.str("type")!!.contains('.'))
        }
    }

    @Test fun tarExtractsRegularFilesAndDirs() {
        fun hdr(name: String, size: Int, type: Char): ByteArray {
            val h = ByteArray(512)
            name.toByteArray().copyInto(h)
            "0000644".toByteArray().copyInto(h, 100)
            "%011o".format(size).toByteArray().copyInto(h, 124)
            h[156] = type.code.toByte()
            "ustar".toByteArray().copyInto(h, 257)
            return h
        }
        val raw = ByteArrayOutputStream()
        raw.write(hdr("d/", 0, '5'))
        val body = "hello".toByteArray()
        raw.write(hdr("d/a.txt", body.size, '0')); raw.write(body); raw.write(ByteArray(512 - body.size))
        raw.write(ByteArray(1024))
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(raw.toByteArray()) } }
        val dir = File(System.getProperty("java.io.tmpdir"), "tar-test-${System.nanoTime()}").apply { mkdirs() }
        val tgz = File(dir, "t.tar.gz").apply { writeBytes(gz.toByteArray()) }
        val out = File(dir, "out")
        val r = TarExtractor.extract(tgz, out)
        assertEquals(1, r.files)
        assertEquals("hello", File(out, "d/a.txt").readText())
        dir.deleteRecursively()
    }
}

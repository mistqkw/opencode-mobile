package app.ocmobile.data

import android.database.sqlite.SQLiteDatabase
import app.ocmobile.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Search through chat text and memory. OpenCode keeps conversations in SQLite inside Ubuntu
 * (~/.local/share/opencode/opencode.db); the database is opened read-only while the server keeps running.
 * With a remote server there's no database on the phone, so only memory is searched and the list itself searches titles.
 */
object Search {
    class Hit(val sessionId: String, val title: String, val snippet: String, val user: Boolean, val time: Long)
    class MemoryHit(val shelf: String, val line: String)

    private const val DB = "/root/.local/share/opencode/opencode.db"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chats(q: String, limit: Int = 60): List<Hit> = withContext(Dispatchers.IO) {
        if (q.length < 2 || Graph.devices.isRemote) return@withContext emptyList()
        val f = Graph.paths.host(DB)
        if (!f.exists()) return@withContext emptyList()
        runCatching {
            SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
                // only message text and only main chats (no team helpers)
                db.rawQuery(
                    """SELECT p.session_id, s.title, p.data, m.data, p.time_created FROM part p
                       JOIN session s ON s.id = p.session_id JOIN message m ON m.id = p.message_id
                       WHERE s.parent_id IS NULL AND p.data LIKE ? ESCAPE '\' AND p.data LIKE '%"type":"text"%'
                       ORDER BY p.time_created DESC LIMIT 400""",
                    arrayOf(like),
                ).use { cur ->
                    val seen = HashMap<String, Int>()
                    val out = ArrayList<Hit>()
                    while (cur.moveToNext() && out.size < limit) {
                        val sid = cur.getString(0)
                        if ((seen[sid] ?: 0) >= 3) continue          // at most three matches per chat
                        val text = runCatching { (json.parseToJsonElement(cur.getString(2)) as JsonObject)["text"]?.jsonPrimitive?.content }.getOrNull() ?: continue
                        val at = text.indexOf(q, ignoreCase = true)
                        if (at < 0) continue
                        val role = runCatching { (json.parseToJsonElement(cur.getString(3)) as JsonObject)["role"]?.jsonPrimitive?.content }.getOrNull()
                        seen[sid] = (seen[sid] ?: 0) + 1
                        out += Hit(sid, cur.getString(1), snippet(text, at, q.length), role == "user", cur.getLong(4))
                    }
                    out
                }
            }
        }.getOrDefault(emptyList())
    }

    fun memory(q: String): List<MemoryHit> {
        if (q.length < 2) return emptyList()
        return runCatching {
            Memory.shelves().flatMap { f -> Memory.facts(f).filter { it.contains(q, true) }.map { MemoryHit(f.nameWithoutExtension, it.trim().removePrefix("- ")) } }.take(30)
        }.getOrDefault(emptyList())
    }

    /** A piece of text around the match, on one line. */
    private fun snippet(text: String, at: Int, len: Int): String {
        val from = (at - 50).coerceAtLeast(0)
        val to = (at + len + 90).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace(Regex("\\s+"), " ").trim() + if (to < text.length) "…" else ""
    }
}

package app.ocmobile.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph

/**
 * Connecting to a computer by QR code. The code holds a link
 * `cl0udly://connect?n=<name>&u=<server address>&p=<password>`, shown by `tools/connect-qr.sh`
 * (and later by the desktop app). Scanned in the app or opened as a link:
 * first we ask "connect?", and only then add the device.
 */
object ConnectLink {
    data class Offer(val name: String, val url: String, val password: String)

    /** Waits for confirmation in the dialog (Root). */
    var pending by mutableStateOf<Offer?>(null)

    fun parse(raw: String): Offer? = runCatching {
        val u = Uri.parse(raw.trim())
        if (!u.scheme.equals("cl0udly", true) || !u.host.equals("connect", true)) return null
        val url = u.getQueryParameter("u")?.takeIf { it.isNotBlank() } ?: return null
        Offer(u.getQueryParameter("n").orEmpty(), Devices.normalize(url), u.getQueryParameter("p").orEmpty())
    }.getOrNull()

    /** A link or text from a QR code; false means it isn't a connect code. */
    fun offer(raw: String): Boolean {
        val o = parse(raw) ?: return false
        pending = o
        return true
    }

    /** Checks the server, adds (or updates) the device and makes it active. */
    suspend fun accept(o: Offer): Device {
        Graph.api.probe(o.url, o.password)
        Graph.devices.list.value.filter { it.url == o.url }.forEach { Graph.devices.remove(it.id) }
        val d = Graph.devices.add(o.name, o.url, o.password)
        Graph.devices.select(d.id)
        return d
    }
}

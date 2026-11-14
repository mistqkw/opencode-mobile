package app.ocmobile.runtime

import app.ocmobile.data.Prefs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real-root runtime: when KernelSU (or any `su`) is granted, run the Ubuntu rootfs with an actual
 * `chroot` instead of proot. No syscall emulation / seccomp, a real `/proc`, and genuine root inside
 * the guest. Falls back to proot when root isn't available.
 *
 * Android's toybox has no private mount-namespace support (`unshare -m` leaks mounts to the global
 * namespace and `mount --make-rprivate` is unsupported), so — like every Linux-on-Android chroot — we
 * bind `/dev /proc /sys` into the rootfs once, idempotently, and leave them persistent across
 * commands. [RootCommand.teardown] unmounts them, and must run before the rootfs is deleted.
 */
object RootShell {
    @Volatile private var cached: Boolean? = null

    /** True when `su` grants root AND `chroot` exists. */
    fun ready(): Boolean = cached ?: recheck()

    /** Re-run the probe (triggers the KernelSU grant prompt the first time). Call off the main thread. */
    fun recheck(): Boolean {
        val rooted = run("id -u").let { (code, out) -> code == 0 && out.trim() == "0" }
        val hasChroot = rooted && run("command -v chroot >/dev/null 2>&1").first == 0
        val ok = rooted && hasChroot
        cached = ok
        return ok
    }

    fun reset() { cached = null }

    private fun run(cmd: String): Pair<Int, String> = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); -1 to out } else p.exitValue() to out
    } catch (e: Exception) {
        -1 to (e.message ?: e.javaClass.simpleName)
    }
}

/** Builds the `su -c` command line that chroots into the rootfs and runs [guestArgv]. */
class RootCommand(private val paths: RuntimePaths, @Suppress("unused") private val prefs: Prefs) {

    /** Root mode runs native arm64 ELF directly, so the process needs no special host environment. */
    fun env(): Map<String, String> = emptyMap()

    fun build(
        guestArgv: List<String>,
        cwd: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): List<String> {
        paths.tmp.mkdirs()
        val guestEnv = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "LOGNAME" to "root",
            "SHELL" to "/bin/bash",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "TMPDIR" to "/tmp",
            "SSL_CERT_FILE" to "/etc/ssl/certs/ca-certificates.crt",
            "NODE_EXTRA_CA_CERTS" to "/etc/ssl/certs/ca-certificates.crt",
            "DEBIAN_FRONTEND" to "noninteractive",
        ).apply { putAll(extraEnv) }

        val rf = paths.rootfs.path
        val envPairs = guestEnv.entries.joinToString(" ") { sq("${it.key}=${it.value}") }
        val argv = guestArgv.joinToString(" ") { sq(it) }

        // Idempotent binds (grep-guarded so repeated calls don't stack mounts); then chroot + exec.
        // The guest cmd is passed positionally so no inner quoting is needed: $1 = cwd, then argv.
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("RF=${sq(rf)}")
            appendLine("mkdir -p \"\$RF/dev\" \"\$RF/proc\" \"\$RF/sys\" \"\$RF/dev/pts\" \"\$RF/dev/shm\" \"\$RF/tmp\"")
            appendLine("grep -qF \" \$RF/dev \" /proc/mounts || mount -o bind /dev \"\$RF/dev\"")
            appendLine("grep -qF \" \$RF/proc \" /proc/mounts || mount -t proc proc \"\$RF/proc\" || mount -o bind /proc \"\$RF/proc\"")
            appendLine("grep -qF \" \$RF/sys \" /proc/mounts || mount -o bind /sys \"\$RF/sys\"")
            appendLine("grep -qF \" \$RF/dev/pts \" /proc/mounts || mount -t devpts devpts \"\$RF/dev/pts\" 2>/dev/null")
            appendLine("grep -qF \" \$RF/dev/shm \" /proc/mounts || mount -t tmpfs tmpfs \"\$RF/dev/shm\" 2>/dev/null")
            append("exec chroot \"\$RF\" /usr/bin/env -i ")
            append(envPairs)
            append(" /bin/sh -c 'cd \"\$1\" || exit 1; shift; exec \"\$@\"' sh ")
            append(sq(cwd))
            append(" ")
            append(argv)
            append("\n")
        }
        return listOf("su", "-c", "sh ${sq(writeScript("ocroot", script))}")
    }

    /** Kill the chrooted server. A root-launched `opencode` is reparented out of the app, so the
     *  app's Process.destroy() can't reach it — without this a mode switch leaves it holding the
     *  port and the next server can't bind. Best-effort, blocking (≤8s); call off the main thread. */
    fun killServer() {
        try {
            val p = ProcessBuilder(
                "su", "-c",
                "pkill -TERM -f 'opencode serve' 2>/dev/null; sleep 1; pkill -KILL -f 'opencode serve' 2>/dev/null; exit 0",
            ).redirectErrorStream(true).start()
            if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {}
    }

    /** Unmount the persistent binds. MUST run before the rootfs is deleted, or a recursive delete
     *  would descend into a live /proc. No-op (and no `su` prompt) when nothing is mounted. */
    fun teardown() {
        val rf = paths.rootfs.path
        val mounted = try {
            File("/proc/mounts").readLines().any { it.contains(" $rf/") }
        } catch (_: Exception) { false }
        if (!mounted) return
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("RF=${sq(rf)}")
            // Children first (dev/pts, dev/shm) before their parents.
            appendLine("for m in dev/pts dev/shm dev proc sys; do")
            appendLine("  grep -qF \" \$RF/\$m \" /proc/mounts && umount \"\$RF/\$m\" 2>/dev/null")
            appendLine("done")
        }
        try {
            val p = ProcessBuilder("su", "-c", "sh ${sq(writeScript("ocumount", script))}")
                .redirectErrorStream(true).start()
            if (!p.waitFor(15, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {}
    }

    private fun writeScript(prefix: String, text: String): String {
        // Purge previous launchers of this kind so they don't pile up across restarts. The running
        // server already exec'd off its script, so it no longer holds the file.
        paths.tmp.listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".sh") }
            ?.forEach { runCatching { it.delete() } }
        val f = File.createTempFile(prefix, ".sh", paths.tmp)
        f.writeText(text)
        return f.path
    }

    /** POSIX single-quote: wrap in '…' and escape embedded quotes as '\''. */
    private fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

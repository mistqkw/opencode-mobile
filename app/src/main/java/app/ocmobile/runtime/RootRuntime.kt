package app.ocmobile.runtime

import app.ocmobile.data.Prefs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real-root runtime: when KernelSU (or any `su`) is granted, run the Ubuntu rootfs with an actual
 * `chroot` inside a private mount namespace instead of proot. No syscall emulation / seccomp, a real
 * `/proc`, and genuine root inside the guest. Falls back to proot when root isn't available.
 */
object RootShell {
    @Volatile private var cached: Boolean? = null

    /** True when `su` grants root AND `unshare` exists (needed for a clean, self-cleaning mount ns). */
    fun ready(): Boolean = cached ?: recheck()

    /** Re-run the probe (triggers the KernelSU grant prompt the first time). Call off the main thread. */
    fun recheck(): Boolean {
        val rooted = run("id -u").let { (code, out) -> code == 0 && out.trim() == "0" }
        val hasUnshare = rooted && run("command -v unshare >/dev/null 2>&1").first == 0
        val ok = rooted && hasUnshare
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

        val rf = sq(paths.rootfs.path)
        val envPairs = guestEnv.entries.joinToString(" ") { sq("${it.key}=${it.value}") }
        val argv = guestArgv.joinToString(" ") { sq(it) }

        // The guest cmd is passed positionally so no inner quoting is needed: $1 = cwd, then argv.
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("RF=$rf")
            // Re-exec into a private mount namespace so our binds don't leak and clean up on exit.
            appendLine("if [ -z \"\$OCM_NS\" ] && command -v unshare >/dev/null 2>&1; then export OCM_NS=1; exec unshare -m /system/bin/sh \"\$0\"; fi")
            appendLine("mkdir -p \"\$RF/dev\" \"\$RF/proc\" \"\$RF/sys\" \"\$RF/dev/pts\" \"\$RF/dev/shm\" \"\$RF/tmp\"")
            appendLine("mount -o bind /dev \"\$RF/dev\" 2>/dev/null")
            appendLine("mount -t proc proc \"\$RF/proc\" 2>/dev/null || mount -o bind /proc \"\$RF/proc\" 2>/dev/null")
            appendLine("mount -o bind /sys \"\$RF/sys\" 2>/dev/null")
            appendLine("mount -t devpts devpts \"\$RF/dev/pts\" 2>/dev/null")
            appendLine("mount -t tmpfs tmpfs \"\$RF/dev/shm\" 2>/dev/null")
            append("exec chroot \"\$RF\" /usr/bin/env -i ")
            append(envPairs)
            append(" /bin/sh -c 'cd \"\$1\" || exit 1; shift; exec \"\$@\"' sh ")
            append(sq(cwd))
            append(" ")
            append(argv)
            append("\n")
        }

        val f = File.createTempFile("ocroot", ".sh", paths.tmp)
        f.writeText(script)
        return listOf("su", "-c", "sh ${sq(f.path)}")
    }

    /** POSIX single-quote: wrap in '…' and escape embedded quotes as '\''. */
    private fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

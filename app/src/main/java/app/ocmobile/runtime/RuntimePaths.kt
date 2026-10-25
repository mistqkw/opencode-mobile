package app.ocmobile.runtime

import android.content.Context
import java.io.File

/** Где что лежит: proot из jniLibs (исполняемый), rootfs и временные файлы — в filesDir. */
class RuntimePaths(ctx: Context) {
    val nativeDir: String = ctx.applicationInfo.nativeLibraryDir
    val proot = File(nativeDir, "libproot.so")
    val prootLoader = File(nativeDir, "libproot-loader.so")

    private val base: File = ctx.filesDir
    val rootfs = File(base, "rootfs")
    /** Копия libtalloc.so под именем libtalloc.so.2 — jniLibs не умеют таких имён. */
    val libDir = File(base, "lib")
    val tmp = File(base, "tmp")
    val fake = File(base, "fake")
    val downloads = File(base, "dl")
    val exports = File(base, "export")

    val opencodeBin = File(rootfs, "usr/local/bin/opencode")
    val globalAgentsMd = File(rootfs, "root/.config/opencode/AGENTS.md")
    val rootfsReady get() = File(rootfs, "etc/os-release").exists() && File(rootfs, ".oc-configured").exists()

    fun host(guestPath: String) = File(rootfs, guestPath.trimStart('/'))
}

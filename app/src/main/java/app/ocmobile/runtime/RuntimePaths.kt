package app.ocmobile.runtime

import android.content.Context
import java.io.File

/** Where things live: proot from jniLibs (executable), rootfs and temp files in filesDir. */
class RuntimePaths(ctx: Context) {
    val nativeDir: String = ctx.applicationInfo.nativeLibraryDir
    val proot = File(nativeDir, "libproot.so")
    val prootLoader = File(nativeDir, "libproot-loader.so")
    /** False on 32-bit phones (the armeabi-v7a build has no proot): OpenCode exists only for arm64, so only a computer can be used. */
    val localSupported get() = proot.exists() && android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    private val base: File = ctx.filesDir
    val rootfs = File(base, "rootfs")
    /** A copy of libtalloc.so named libtalloc.so.2: jniLibs can't hold such names. */
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

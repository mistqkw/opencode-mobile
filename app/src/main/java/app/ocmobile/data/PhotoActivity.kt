package app.ocmobile.data

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/** Transparent screen for the phone.take_photo tool: opens the camera and hands the photo to the bridge. */
class PhotoActivity : ComponentActivity() {
    private var file: File? = null

    private val shoot = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        PhoneBridge.photo?.complete(file?.takeIf { ok && it.length() > 0 })
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // after a recreate (rotation, low memory) the camera is already open, just remember the file
        savedInstanceState?.getString("file")?.let { file = File(it); return }
        val f = File(File(cacheDir, "camera").apply { mkdirs() }, "agent-${System.currentTimeMillis()}.jpg")
        file = f
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        runCatching { shoot.launch(uri) }.onFailure { PhoneBridge.photo?.complete(null); finish() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        file?.let { outState.putString("file", it.path) }
    }
}
